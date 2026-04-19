package com.wj.glasses.utils;
public class Constants {
    /**
     * +message package name
     */
    public static final String WMC_APP_PACKAGE = "com.wj.GlassesServer";




    public static final String BLUE_OPEN_TIME = "blue_open_time";
    public static final String BLUE_CLOSE_TIME = "blue_close_time";


    public static final String ACTION_START_OTA = "com.sim.action.START_OTA";
    public static final String ACTION_TAKE_PHOTO = "com.sim.TAKE_PHOTO_ACTION";
    public static final String ACTION_START_RSTP = "com.sim.action.SEND_RSTP_ACTION";

    public static final int FILE_PORT = 7777;
    public static final int FLAG_FILE = 1;

    public static final int FILE_OTA_PORT = 7778;


    public static final int ACTION_STATE_CHANGED = 0x103;

    public static final int BLUE_STATE_OPEN = 0x106;

    public static final int BLUE_STATE_OFF = 0x107;

    public static final int WIFI_STATE_OPEN = 0x201;
    public static final int WIFI_STATE_OFF = 0x202;
    public static final int WIFI_SCAN_RESULT = 0x203;
    public static final int WIFI_CONNECT_DEVICE = 0x204;
    public static final int WIFI_DISCONNECT = 0x205;

    public static final int TRANSFORM_PICTURE = 0x206;
    public static final int CMD_START_OTA = 0x208;
    public static final int CMD_TAKE_PHOTO = 0x209;

    public static int mLastBattery=0;


    public static boolean isTestMsgApi = false;
    public static int currDeviceOrientation = 0;

}