package com.wj.glasses;

import static com.wj.glasses.utils.Constants.TRANSFORM_PICTURE;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.glasses.GlassesManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import com.wj.glasses.entity.Media;
import com.wj.glasses.utils.Constants;
import com.wj.glasses.utils.GlassesLog;
import com.wj.glasses.utils.EventHelper;
import com.wj.glasses.utils.EventInfo;
import com.wj.glasses.utils.Utils;
import com.wj.glasses.view.AutoFitTextureView;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TakePictrueService extends IntentService implements TextureView.SurfaceTextureListener,EventHelper.CallBack
{
    private static final String TAG = "TakePictrueService";
    private WindowManager windowManager;
    private static boolean isDestroy = false;
    private boolean allreadyOpen = false;

    /**
     * 默认使用前置摄像头: CameraCharacteristics.LENS_FACING_FRONT，0
     * 背后摄像头为：CameraCharacteristics.LENS_FACING_BACK，1
     */
    private int mFacing = CameraCharacteristics.LENS_FACING_FRONT;
    /**
     * 输出图片宽度
     */
    private int pictrueWidth = 720;
    /**
     * 输出图片高度
     */
    private int pictrueHeight = 1280;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;
    /**
     * This is the output file for our picture.
     */
    private File mFile;
    static boolean isSelf;
    OrientationEventListener orientationEventListener;
    private GlassesManager mGlassesManager;
    private final byte[] RedBytes = { 0x00, (byte)255, 0x01, 0x00, 0x00 };
    private final byte[] OffBytes = { 0x00, 0x00, 0x00, 0x00, 0x00 };
    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation = 0;
    int currentOrientation = 0;
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private final int LUCK_FOCUS = 1;



    public TakePictrueService()
    {
        super(TAG);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        try
        {
            initOrientationDetection();
            startBackgroundThread();
            isDestroy = false;
            GlassesLog.d("TakePictrueService onCreate");
            if(!isCameraAvailable(this)){
                stopSelf();
            }
            wakeup(this);
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                stopSelf();
            }else{
                windowManager = (WindowManager) TakePictrueService.this.getSystemService(Context.WINDOW_SERVICE);
                mTextureView = new AutoFitTextureView(this);
                GlassesLog.d("onCreate currentOrientation:"+Constants.currDeviceOrientation);
                WindowManager.LayoutParams layoutParams=null;
                if(Constants.currDeviceOrientation ==90||Constants.currDeviceOrientation==270){
                    layoutParams = new WindowManager.LayoutParams(
                            pictrueWidth, pictrueHeight,
                            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );
                }else{
                    layoutParams = new WindowManager.LayoutParams(
                            pictrueWidth, pictrueHeight,
                            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT);
                }
                layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
                windowManager.addView(mTextureView, layoutParams);
                mTextureView.setSurfaceTextureListener(this);
            }
        }catch (Exception e){
            e.printStackTrace();
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
    public void onStart(@Nullable Intent intent, int startId)
    {
        GlassesLog.d("TakePictrueService onStart");
        super.onStart(intent, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent)
    {
        GlassesLog.d("TakePictrueService onHandleIntent isDestroy:"+isDestroy);
        isSelf=intent.getBooleanExtra("is_self",false);
        isDestroy = false;
        while (!isDestroy)
        {
            if(allreadyOpen){
                long currentTimeMillis = System.currentTimeMillis();
                Date date = new Date(currentTimeMillis);
                SimpleDateFormat formatter  = new SimpleDateFormat("yyyyMMdd_HHmmss");
                formatter .setTimeZone(TimeZone.getDefault());
                String formattedTime = formatter .format(date);
                String name = "picture_" + formattedTime + ".jpg";
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/pictrue");
                if(!dir.exists()) {
                    dir.mkdir();
                }
                mFile = new File(dir.getAbsolutePath(),name);
                GlassesLog.d("TakePictrueService onHandleIntent while do takePicture name->"+name);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                takePicture();
            }
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        GlassesLog.d("TakePictrueService onDestroy");
        orientationEventListener.disable();
        orientationEventListener = null;
        releaseWake();
        closeCamera();
        stopBackgroundThread();
        windowManager.removeView(mTextureView);
        Utils.setCameraState(true);
        try{
            mGlassesManager.sendCmd(mGlassesManager.processMcuLedData(GlassesManager.SET_LED_STATUS, OffBytes, GlassesManager.LED_NOTIFICATION));
        }catch(Exception e){
            e.printStackTrace();
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
        GlassesLog.d("TakePictrueService onSurfaceTextureAvailable width->"+width+" height->"+height);
        openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
        GlassesLog.d("TakePictrueService onSurfaceTextureSizeChanged");
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        GlassesLog.d("TakePictrueService onSurfaceTextureDestroyed");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
        GlassesLog.d("TakePictrueService onSurfaceTextureUpdated");
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            GlassesLog.d("TakePictrueService duyi mStateCallback onOpened");
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            GlassesLog.d("TakePictrueService duyi mStateCallback onDisconnected");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            GlassesLog.d("TakePictrueService duyi mStateCallback onError");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };



    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            GlassesLog.d("TakePictrueService duyi mOnImageAvailableListener onImageAvailable");
            MediaScannerConnection.scanFile(
                    TakePictrueService.this,
                    new String[]{mFile.getAbsolutePath()},
                    new String[]{"picture/jpg"},
                    null);
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };

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
        GlassesLog.d("TakePictrueService mWakeLock release");
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
            }
        }
    }



    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            //GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState);
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    GlassesLog.d("TakePictrueService duyi mCaptureCallback process afState-->"+afState);
                    if (afState == null) {
                        GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState+" captureStillPicture");
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState+" captureStillPicture");
                            captureStillPicture();
                        } else {
                            GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState+" runPrecaptureSequence");
                            runPrecaptureSequence();
                        }
                    }else{
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState+" captureStillPicture");
                            captureStillPicture();
                        } else {
                            GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState+" runPrecaptureSequence");
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        //GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState);
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        GlassesLog.d("TakePictrueService duyi mCaptureCallback process mState-->"+mState+" captureStillPicture");
                        captureStillPicture();
                    }
                    break;
                }
            }
        }


        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            //GlassesLog.d("TakePictrueService duyi mCaptureCallback onCaptureProgressed mState process");
            if(!isDestroy)
                process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            //GlassesLog.d("TakePictrueService duyi mCaptureCallback onCaptureCompleted mState process");
            if(!isDestroy){
                process(result);
            }

        }

    };
    private void initOrientationDetection(){
        orientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                GlassesLog.d("initOrientationDetection orientation:"+orientation);
                if(orientation == ORIENTATION_UNKNOWN)return;
                currentOrientation=roundOrientation(orientation);
            }
        };
        if(orientationEventListener.canDetectOrientation()){
            orientationEventListener.enable();
        }
    }
    private int roundOrientation(int orientation){
        return (orientation+45)/90*90%360;
    }
    private int getJpegRotation(int sensorOrientation,int deviceOrientation){
        GlassesLog.d("duyi getJpegRotation sensorOrientation:"+sensorOrientation+" deviceOrientation:"+deviceOrientation);
        if(isFrontCamera(mCameraId)){
            return(360-((sensorOrientation+deviceOrientation)%360))%360;
        }else{
            return (sensorOrientation+deviceOrientation)%360;
        }
    }
    private boolean isFrontCamera(String cameraId){
        try{
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return  false;
        }
    }
    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        GlassesLog.d("duyi w:"+w+" h:"+h);
        Size currentSize = null;
        for (Size option : choices) {
            GlassesLog.d("duyi option.getWidth:"+option.getWidth()+" option.getHeight:"+option.getHeight());
            if (option.getWidth() == textureViewWidth && option.getHeight() == textureViewHeight ) {
                currentSize = option;
            }
        }
        return currentSize;

    }




    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        GlassesLog.d("TakePictrueService duyi setUpCameraOutputs width->"+width+" height->"+height);
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            if(mCameraId == null){
                mCameraId =manager.getCameraIdList()[0];
            }
            if(mCameraId== null){
                stopSelf();
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // For still image captures, we use the largest available size.
            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new CompareSizesByArea());
            int orientation=getJpegRotation(mSensorOrientation,currentOrientation);
            GlassesLog.d("setUpCameraOutputs orientation:"+orientation);
            if(orientation==90||orientation==270){
                //设置图片的大小，格式
                mImageReader = ImageReader.newInstance(pictrueHeight, pictrueWidth, ImageFormat.JPEG, 2);
            }else{
                //设置图片的大小，格式
                mImageReader = ImageReader.newInstance(pictrueWidth, pictrueHeight, ImageFormat.JPEG, 2);
            }
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            int displayRotation = windowManager.getDefaultDisplay().getRotation();
            //noinspection ConstantConditions
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            GlassesLog.d("TakePictrueService setUpCameraOutputs displayRotation-->"+displayRotation);
            GlassesLog.d("TakePictrueService setUpCameraOutputs mSensorOrientation-->"+displayRotation);
            if(getJpegRotation(mSensorOrientation,currentOrientation)==90||getJpegRotation(mSensorOrientation,currentOrientation)==270){
                swappedDimensions = true;
            }else{
                swappedDimensions = false;
            }
            Point displaySize = new Point();
            windowManager.getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                GlassesLog.d("TakePictrueService duyi 002 swappedDimensions");
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }
            if(maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }
            if(maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest);

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation1 = getResources().getConfiguration().orientation;
            GlassesLog.d("TakePictrueService duyi 002 orientation-->"+orientation);
            mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            // Check if the flash is supported.
            Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mFlashSupported = available == null ? false : available;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the camera specified by .
     */
    private void openCamera(int width, int height) {
        GlassesLog.d("TakePictrueService duyi openCamera++++");
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            allreadyOpen = true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mCameraOpenCloseLock.release();
        }
        allreadyOpen = false;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            GlassesLog.d("TakePictrueService duyi createCameraPreviewSession");
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.i(TAG,e.toString());
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                CameraCaptureSession cameraCaptureSession) {
                            Log.i(TAG,"Failed");
                        }
                    }, mBackgroundHandler
            );
        } catch (CameraAccessException e) {
            Log.i(TAG,e.toString());
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        GlassesLog.d("TakePictrueService duyi configureTransform++++");
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = windowManager.getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        GlassesLog.d("TakePictrueService duyi call mTextureView.setTransform");
        mTextureView.setTransform(matrix);
        GlassesLog.d("TakePictrueService duyi configureTransform--------");
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            GlassesLog.d("duyi lockFocus");
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            if(mCaptureSession!=null)
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            GlassesLog.d("TakePictrueService duyi runPrecaptureSequence");
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            if(mCaptureSession!=null)
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            GlassesLog.d("TakePictrueService duyi captureStillPicture");
            if ( null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = windowManager.getDefaultDisplay().getRotation();
            GlassesLog.d("TakePictrueService duyi captureStillPicture rotation->"+rotation);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegRotation(mSensorOrientation,currentOrientation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    GlassesLog.d("TakePictrueService Saved: " + mFile.getName());
                    unlockFocus();
                }
            };
            if(mCaptureSession!=null){
                mCaptureSession.stopRepeating();
                mCaptureSession.abortCaptures();
                mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            GlassesLog.d("TakePictrueService duyi unlockFocus");
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            if(mCaptureSession!=null){
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
                // After this, the camera will go back to the normal state of preview.
                mState = STATE_PREVIEW;
                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                        mBackgroundHandler);
            }

        } catch (CameraAccessException e) {
            Log.i(TAG,e.toString());
        }
    }



    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            GlassesLog.d("TakePictrueService duyi setAutoFlash");
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    @Override
    public void listen(EventInfo info) {
        if(info.getWhat() == TRANSFORM_PICTURE){
            GlassesLog.d("listen close service");
            stopSelf();
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable
    {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            GlassesLog.d("TakePictrueService duyi ImageSaver+++++++");
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
                output.flush();

                if(TakePictrueService.isSelf){
                    Media p = new Media();
                    p.name= mFile.getName();
                    p.path=mFile.getAbsolutePath();
                    p.mediaType = 1;
                    GlassesApplication.getApplication().insert(p);
                }else{
                    EventInfo info = new EventInfo();
                    info.setWhat(TRANSFORM_PICTURE);
                    info.setInfo(mFile.getAbsolutePath());
                    EventHelper.getInstance().post(info);
                    GlassesLog.d("TakePictrueService 获取照片成功");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {

                isDestroy = true;
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        Log.i(TAG,e.toString());
                    }
                }
            }
        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size>
    {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}