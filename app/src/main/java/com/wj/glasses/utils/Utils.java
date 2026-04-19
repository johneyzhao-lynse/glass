package com.wj.glasses.utils;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.provider.Settings;
import java.util.List;

public class Utils {
    private static PowerManager.WakeLock mWakeLock;

    public static boolean isSysApp(Context context) {
        List<PackageInfo> packageInfoLis = context.getPackageManager().getInstalledPackages(PackageManager.PERMISSION_GRANTED);
        for (PackageInfo packageInfo : packageInfoLis) {
            if (packageInfo.packageName.equals(context.getPackageName())) {
                if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0) {
                    // 系统应用
                    return true;
                } else {
                    // 非系统应用
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 亮屏操作
     */
    public static void wakeup(Context context) {
        if (mWakeLock != null) {
            return;
        }
        wakeup(context, false);
    }

    /**
     * 亮屏操作
     */
    public static void wakeup(Context context, boolean isOnce) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag:CpuKeepRunning");
        try {
            if (isOnce) {
                wakeLock.acquire(400);
            } else {
                wakeLock.acquire();
                mWakeLock = wakeLock;
            }
        } catch (Exception e) {
        }
    }

    /**
     * 亮屏操作
     */
    public static void release() {
        GlassesLog.d("mWakeLock release");
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
            }
        }
    }

    public static int getIntSettingsValue(Context context, String name) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), name);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void putIntSettingsValue(Context context, String key, int value) {
        try {
            Settings.Global.putInt(context.getContentResolver(), key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static String getSettingsValue(Context context, String name) {
        return Settings.Global.getString(context.getContentResolver(), name);
    }

    public static void putSettingsValue(Context context, String key, String value) {
        try {
            Settings.Global.putString(context.getContentResolver(), key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isCameraFree = true;
    public static void setCameraState(boolean value){
        isCameraFree = value;
    }
    public static boolean getCameraState(){
        return isCameraFree;
    }
}
