package com.wj.glasses;

import android.annotation.NonNull;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.glasses.GlassesManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.wj.glasses.rtsp.RtspMgr;
import com.wj.glasses.utils.GlassesLog;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private boolean isRefuse = false;

    private List<String> deniedPermissionList = new ArrayList<>();

    public static final String ACTION_RTSP_START = "action.rtsp.start";
    public static final String ACTION_RTSP_STOP = "action.rtsp.stop";

    public RtspBroadcast rtspBroadcast;
    private GlassesManager mGlassesManager;
    private static PowerManager.WakeLock mWakeLock;

    Button btn_start;
    Button btn_stop;
    TextView tv_url;
    TextView tv_show;
    SurfaceView sfView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GlassesLog.d("MainActivity onCreate");
        setContentView(R.layout.activity_main);
        mGlassesManager = (GlassesManager) getSystemService(Context.GLASSES_SERVICE);
        initView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(rtspBroadcast);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 1024 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    isRefuse = false;
                } else isRefuse = true;
            }
        }
    }

    private void initView() {
        sfView = findViewById(R.id.sfView);
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);
        tv_url = findViewById(R.id.tv_url);
        tv_show = findViewById(R.id.tv_show);
        btn_start.setOnClickListener(v -> {
            startRtsp();
        });
        btn_stop.setOnClickListener(v -> {
            stopRtsp();
        });

        //注册广播实例
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_RTSP_START);
        intentFilter.addAction(ACTION_RTSP_STOP);
        rtspBroadcast = new RtspBroadcast();
        registerReceiver(rtspBroadcast, intentFilter);

        sfView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                GlassesLog.d("MainActivity surfaceCreated");
                startRtsp();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                GlassesLog.d("MainActivity surfaceDestroyed");
                stopRtsp();
            }
        });
    }

    public class RtspBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            GlassesLog.d(action);
            if (action == ACTION_RTSP_START) {
                startRtsp();
            } else if (action == ACTION_RTSP_STOP) {
                stopRtsp();
            }
        }
    }

    public void startRtsp() {
        GlassesLog.d("MainActivity start startRtsp");
        setKeepScreenOn(true);
        if (RtspMgr.getInstance().startRtsp(sfView)) {

            if (RtspMgr.getInstance().isStreaming()) {
                GlassesLog.d("MainActivity start isStreaming");
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_RTSP_START,
                        RtspMgr.getInstance().getUri().getBytes()));
            }
            tv_url.setText(RtspMgr.getInstance().getUri());
        }
    }

    public void stopRtsp() {
        GlassesLog.d("MainActivity start stopRtsp");
        setKeepScreenOn(false);
        RtspMgr.getInstance().stopRtsp();
        tv_url.setText(null);
        releaseWake();
        finish();
    }

    /**
     * wakeup
     */
    public static void wakeup(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag:rtspRunning");
        try {
            wakeLock.acquire();
            mWakeLock = wakeLock;
        } catch (Exception e) {
        }
    }

    /**
     * releaseWake
     */
    public static void releaseWake() {
        GlassesLog.d("MainActivity mWakeLock release");
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
            }
        }
    }


    private void setKeepScreenOn(boolean enable) {
        GlassesLog.d("MainActivity setKeepScreenOn " + enable);
        if (!GlassesApplication.isRtspForeground()) return;
        if (enable) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public static void launch(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private void showTv(String msg) {
        if (isDestroyed())
            return;
        runOnUiThread(() -> tv_show.append(msg + "\n\n"));
    }

}