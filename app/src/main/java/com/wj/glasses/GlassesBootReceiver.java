package com.wj.glasses;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class GlassesBootReceiver extends BroadcastReceiver {
    private static final String TAG = "BleBootReceiver";
    private static final String BOOT_ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        //开机的过程当中,启动 Activity的操作,判断当前启动的动作是开机启动的
        if (BOOT_ACTION.equals(intent.getAction())) {
            //开启Service
            openService(context);
        }
    }

    /***
     * 启动Service的方法
     *
     * @param context
     */
    public void openService(Context context) {
        Intent newIntent = new Intent(context, GlassesServerService.class);
        //判断当前编译的版本是否高于等于 Android8.0 或 26 以上的版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(newIntent);
        } else {
            context.startService(newIntent);
        }
        Log.i(TAG, "start ble Service");
    }
}
