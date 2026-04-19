package com.wj.glasses;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.room.Room;

import com.wj.glasses.entity.Media;
import com.wj.glasses.utils.AppDatabase;
import com.wj.glasses.utils.MediaDao;

import java.util.List;

public class GlassesApplication extends Application {
    private static Toast sToast; // 单例Toast,避免重复创建，显示时间过长
    private static GlassesApplication application;
    public static boolean isRtspForeground;
    private static MediaDao userDao;


    @Override
    public void onCreate() {
        super.onCreate();
        sToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        application = this;
        regAcLife();
        startService();
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "glasses_server").build();
        userDao = db.getMediaDao();
    }

    public static void toast(String txt, int duration) {
        sToast.setText(txt);
        sToast.setDuration(duration);
        sToast.show();
    }

    public void startService() {
        Intent intent = new Intent(this, GlassesServerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
            Log.d("ble", "startForegroundService: ");
        } else {
            startService(intent);
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    public static GlassesApplication getApplication() {
        return application;
    }

    private void regAcLife(){
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
                if (activity.getLocalClassName().equals("MainActivity"))
                    isRtspForeground = true;
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (activity.getLocalClassName().equals("MainActivity"))
                    isRtspForeground = false;
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    public static boolean isRtspForeground(){
        return isRtspForeground;
    }
    public  List<Media> getAll(){
        return userDao.getAll();
    }
    public void insert(Media photo){
        userDao.insert(photo);
    }
    public void deleteOnePhoto(Media photo){
        userDao.deleteOnePhoto(photo);
    }
    public List<Media> getMediaByType(int media_type){
        return userDao.getMedia(media_type);
    }
}
