package com.wj.glasses;

import android.Manifest;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;;
import android.os.Looper;
import android.os.PowerManager;
import android.os.glasses.GlassesManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.wj.glasses.entity.Media;
import com.wj.glasses.utils.EventHelper;
import com.wj.glasses.utils.EventInfo;
import com.wj.glasses.utils.GlassesLog;
import com.wj.glasses.utils.SPUtils;
import com.wj.glasses.utils.Utils;
import com.wj.glasses.view.AutoFitTextureView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

public class RecordVideoService extends IntentService implements TextureView.SurfaceTextureListener,EventHelper.CallBack
{
    private static final String TAG = "RecordVideoService";
    private WindowManager windowManager;
    private AutoFitTextureView mTextureView;
    private Size mPreviewSize;
    private Handler mBackgroundHandler;
    private CameraManager cameraManager;
    private String cameraId;
    MediaRecorder mediaRecorder;
    private CameraDevice cameraDevice;
    private File outputFile;
    private CameraCaptureSession captureSession;
    private boolean isComplete = false;
    SharedPreferences sp;
    private long lastTime;

    private GlassesManager mGlassesManager;
    private final byte[] RedBytes = { 0x00, (byte)255, 0x02, 0x01, 0x00 };
    private final byte[] OffBytes = { 0x00, 0x00, 0x00, 0x00, 0x00 };

    public RecordVideoService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        GlassesLog.d("RecordVideoService onCreate");
        if(!isCameraAvailable(this)){
            stopSelf();
        }
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        startBackgroundThread();
        wakeup(this);
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            stopSelf();
        }else{
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            mTextureView = new AutoFitTextureView(this);
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
            windowManager.addView(mTextureView, layoutParams);

            mTextureView.setSurfaceTextureListener(this);

        }
        Utils.setCameraState(false);
        try{
            mGlassesManager = (GlassesManager) getSystemService(Context.GLASSES_SERVICE);
            mGlassesManager.sendCmd(mGlassesManager.processMcuLedData(GlassesManager.SET_LED_STATUS, RedBytes, GlassesManager.LED_NOTIFICATION));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        GlassesLog.d("RecordVideoService onHandleIntent");
        lastTime = System.currentTimeMillis();
        while(!isComplete){
            long currentTime = System.currentTimeMillis();
            if(currentTime-lastTime>=SPUtils.getInstance(this).getInt("is_take_time",15000)){
                stopRecording();
            }
        }
    }

    private void startRecording() {
        try {
            isComplete = false;
            GlassesLog.d("RecordVideoService startRecording");
            setupMediaRecorder();
            SurfaceTexture previewTexture = mTextureView.getSurfaceTexture();
            previewTexture.setDefaultBufferSize(1280, 720);
            Surface previewSurface = new Surface(previewTexture);
            Surface recorderSurface = mediaRecorder.getSurface();

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, recorderSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                CaptureRequest.Builder recordRequest =
                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                recordRequest.addTarget(previewSurface);
                                recordRequest.addTarget(recorderSurface);

                                session.setRepeatingRequest(recordRequest.build(), null, null);
                                GlassesLog.d("RecordVideoService startRecording 002");
                                mediaRecorder.start();
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            GlassesLog.d("RecordVideoService onConfigureFailed");
                        }
                    },mBackgroundHandler
            );
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder() throws IOException {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoSize(1280, 720);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(10_000_000);
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)+"/video");
        if(!dir.exists())
        {
            dir.mkdir();
        }
        long currentTimeMillis = System.currentTimeMillis();
        Date date = new Date(currentTimeMillis);
        SimpleDateFormat formatter  = new SimpleDateFormat("yyyyMMdd_HHmmss");
        formatter .setTimeZone(TimeZone.getDefault());
        String formattedTime = formatter .format(date);
        outputFile = new File(dir, "video_" + formattedTime + ".mp4");
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.prepare();
    }

    private void stopRecording() {
        try {
            GlassesLog.d("RecordVideoService stopRecording");
            mediaRecorder.stop();
            // 保存视频到媒体库
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{outputFile.getAbsolutePath()},
                    new String[]{"video/mp4"},
                    null
            );
            new Thread(){
                public void run(){
                    Media media=new Media();
                    media.mediaType = 2;
                    media.name=outputFile.getName();
                    media.path = outputFile.getAbsolutePath();
                    GlassesApplication.getApplication().insert(media);
                }
            }.start();

        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(captureSession!=null){
                captureSession.close();
            }
            if(cameraDevice!=null){
                cameraDevice.close();
            }
            if (mediaRecorder != null) {
                mediaRecorder.release();
            }
            isComplete = true;
        }
    }

    private void openCamera() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if(cameraIds.length>0){
                cameraId = cameraIds[0];
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDestroy() {
        GlassesLog.d("RecordVideoService onDestroy");
        super.onDestroy();
        stopBackgroundThread();
        releaseWake();
        Utils.setCameraState(true);
        try{
            mGlassesManager.sendCmd(mGlassesManager.processMcuLedData(GlassesManager.SET_LED_STATUS, OffBytes, GlassesManager.LED_NOTIFICATION));
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public static PowerManager.WakeLock mWakeLock;

    public static void wakeup(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag:rtspRunning");
        try {
            if(powerManager.isScreenOn()){
                wakeLock.acquire();
                mWakeLock = wakeLock;
            }
        } catch (Exception e) {
        }
    }

    /**
     * releaseWake
     */
    public static void releaseWake() {
        GlassesLog.d("mWakeLock release");
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
            }
        }
    }
    public static boolean isCameraAvailable(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
    }



    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraDevice = camera;
            startRecording();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }

    };
    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundHandler = null;
    }

    @Override
    public void listen(EventInfo info) {

    }
}