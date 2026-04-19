package com.wj.glasses.utils;


import android.util.Log;

public final class GlassesLog {
    private static boolean isDebug = true;
    private static final String defaultTag = "glasses-controller";

    public static void d(String msg) {
        if (isDebug && msg != null)
            Log.d(defaultTag, msg);
    }

    public static void i(String msg) {
        if (isDebug && msg != null)
            Log.i(defaultTag, msg);
    }

    public static void w(String msg) {
        if (isDebug && msg != null)
            Log.w(defaultTag, msg);
    }

    public static void e(String msg) {
        if (isDebug && msg != null)
            Log.e(defaultTag, msg);
    }

    public static void setIsDebug(boolean isDebug) {
        GlassesLog.isDebug = isDebug;
    }
}
