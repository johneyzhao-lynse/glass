package com.wj.glasses;

import static com.wj.glasses.utils.Constants.FILE_OTA_PORT;
import static com.wj.glasses.utils.Constants.FILE_PORT;
import static com.wj.glasses.utils.Constants.FLAG_FILE;
import static com.wj.glasses.utils.Constants.TRANSFORM_PICTURE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.glasses.GlassesManager;
import android.os.glasses.IGlassesCallback;
import android.os.glasses.IPadTouchCallback;
import android.os.Message;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.OrientationEventListener;

import androidx.core.app.NotificationCompat;

import com.sim.hardkey.HardKeyManager;
import com.wj.glasses.entity.Media;
import com.wj.glasses.receive.StateReceiver;
import com.wj.glasses.utils.BitUtil;
import com.wj.glasses.utils.GlassesLog;
import com.wj.glasses.utils.Constants;
import com.wj.glasses.utils.EventHelper;
import com.wj.glasses.utils.EventInfo;
import com.wj.glasses.utils.GlassesUtils;
import com.wj.glasses.utils.SPUtils;
import com.wj.glasses.utils.Utils;
import com.wj.glasses.utils.WifiMgr;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GlassesServerService extends Service implements EventHelper.CallBack, HardKeyManager.HardKeyListener {
    private static final String TAG = GlassesServerService.class.getSimpleName();
    private static byte[] HEAD_BYTES = new byte[]{(byte) 0xED, 0x7E, 0x00, 0x02};
    // crc长度占2字节
    private static final int CRC_LENGTH = 2;
    // 内容长度占2字节
    private static final int CONTENT_LENGTH = 4;
    // messageId占2字节
    private static final int MSG_ID_LENGTH = 2;
    // messageIndex占2字节
    private static final int MSG_INDEX_LENGTH = 2;

    private static final int OTHER_LENGTH = CONTENT_LENGTH + MSG_ID_LENGTH + MSG_INDEX_LENGTH + HEAD_BYTES.length;
    private static final int OTHER_LENGTH_WITH_CRC = CONTENT_LENGTH + MSG_ID_LENGTH + MSG_INDEX_LENGTH + HEAD_BYTES.length + CRC_LENGTH;
    private static final byte[] crcBytes = new byte[CRC_LENGTH];
    //通知ID
    private final int NOTIFICATION_SERVER_ID = 0x001;
    //通知通道ID
    private final String CHANNEL_ID = "com.wj.ble.service";
    private final String FOTA_DOWNLOAD_COMPLETE_ACTION = "com.sim.fota.filecomplete";

    public static final Executor EXECUTOR = Executors.newCachedThreadPool();

    public static final String WLAN_AP_SSID = "wlan_ap_ssid";
    public static final String WLAN_AP_PWD = "wlan_ap_pwd";
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private StateReceiver stateReceiver;
    private static GlassesServerService instance;
    HardKeyManager mHardKeyManager;
    private volatile boolean isConnectAp = false;
    private int attemptTimes = 3;

    GlassesUtils mGlassesUtils;
    DataOutputStream mOut;
    Socket mClientSocket;

    boolean isComplete = false;
    Socket clientSocket = null;
    InputStream inputStream = null;
    ServerSocket socketServer;
    GlassesManager mGlassesManager;
    File file;
    private boolean isSelfCall = false;
    private long powerDownTime = 0;
    private long rightKeyDownTime = 0;
    private long lastPowerDownTime = 0;
    private long lastKeyTime = 0;
    PowerManager mPowerManager;
    private PowerManager.WakeLock cpuRunWakeLock;

    //Wear fuction event
    private final int DISCONNECT1_BT_WHAT = 1;
    private final int DISCONNECT2_BT_WHAT = 2;
    private final int RESET1_MCU_WHAT = 3;
    private final int RESET2_MCU_WHAT = 4;
    private final int SHUTDOWN_WHAT = 5;
    private final int TURN_OFF_LED_WHAT = 6;


    private final int DISCONNECT1_DELAY_TIME = 1000 * 15;
    private final int DISCONNECT2_DELAY_TIME = 500;
    private final int RESETMCU_DELAY_TIME = 100;
    private final int SHUTDOWN_DELAY_TIME = 1000 * 60;
    private final int LEDOFF_DELAY_TIME = 1000 * 3;

    //LED type
    private final int RED_BLINK_5_TIMES = 0;
    private final int BLUE_BLINK_5_TIMES = 1;

    /*
      persist.debug.wearmode默认值0，对应Mode0
      WEAR_MODE_NONE-In	             |无	                            |无
      WEAR_MODE_NONE-Out	         |无	                            |无
      WEAR_MODE_AUTO_BT-In	         |蓝牙自动连手机	                    |蓝灯闪烁5次
      WEAR_MODE_AUTO_BT-Out	         |15秒内如无WearIn自动断开蓝牙	    |红灯闪烁5次
      WEAR_MODE_AUTO_SHUTDOWN-In	 |蓝牙自动连手机	                    |蓝灯闪烁5次
      WEAR_MODE_AUTO_SHUTDOWN-Out	 |15秒内如无WearIn自动断开蓝牙；       |红灯闪烁5次
                                     |断开蓝牙后，1分钟内无WearIn自动关机
   */
    /*
    persist.debug.wearmode：the default value is WEAR_MODE_NONE
    WEAR_MODE_NONE：          disalbe wearFuction
    WEAR_MODE_AUTO_BT:        enable wearFuction and BT will auto disconnect after wear out
    WEAR_MODE_AUTO_SHUTDOWN:  enable wearFuction and BT will auto disconnect/device will auto shutdown after wear out
    */
    private final int WEAR_MODE_NONE = 0;
    private final int WEAR_MODE_AUTO_BT = 1;
    private final int WEAR_MODE_AUTO_SHUTDOWN = 2;
    private int wearMode = 0;
    private int mWearHandlerState = 0;
    private OrientationEventListener orientationEventListener;

    public GlassesServerService() {
        mGlassesUtils = new GlassesUtils(this);

    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        GlassesLog.i("onCreate");
        init();
    }

    private void init() {
        mGlassesManager = (GlassesManager) getSystemService(Context.GLASSES_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        cpuRunWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        cpuRunWakeLock.acquire();
        mHardKeyManager = HardKeyManager.getInstance();
        mHardKeyManager.registerHardKeyListener(this);
        EventHelper.getInstance().register(this);
        registerReceiver();
        mGlassesManager.setReadCallback(callback);
        mGlassesManager.setPadTouchListener(new PadTouchListener());
        initOrientationDetection();
        wearMode = SystemProperties.getInt("persist.debug.wearmode", WEAR_MODE_NONE);
    }

    class PadTouchListener extends IPadTouchCallback.Stub {

        @Override
        public void onPadTouch(int[] ints) throws RemoteException {
            for (int i = 0; i < ints.length; i++) {
                if (ints[i] == GlassesManager.TOUCH_PAD_WEAR_IN) {
                    GlassesLog.d("glasses wear in");
                    wakeup(GlassesServerService.this);
                    wearinFuction();
                } else if (ints[i] == GlassesManager.TOUCH_PAD_WEAR_OUT) {
                    GlassesLog.d("glasses wear out");
                    if (false == wearoutFunction()) {
                        releaseWake();
                    }
                }
            }
        }
    }

    public static PowerManager.WakeLock mWakeLock;

    public static void wakeup(Context context) {
        releaseWake();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag:rtspRunning");
        try {
            //if(powerManager.isScreenOn()){
            wakeLock.acquire();
            mWakeLock = wakeLock;
            //}
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

    private boolean isCameraFree() {
        return Utils.getCameraState();
    }

    @Override
    public void onHardKeyEvent(KeyEvent keyEvent) {
        int keyDisabled = SystemProperties.getInt("persist.debug.disablekey", 0);
        if (1 == keyDisabled) {
            return;
        }

        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_POWER) {
            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                long currentTime = System.currentTimeMillis();
                if (isCameraFree() && currentTime - lastKeyTime <= 300) {
                    Intent intent = new Intent(this, TakePictrueService.class);
                    intent.putExtra("is_self", true);
                    startService(intent);
                }
                lastKeyTime = currentTime;
            } else if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                powerDownTime = System.currentTimeMillis();
            }
        } else if (keyEvent.getKeyCode() == 0) {
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                rightKeyDownTime = System.currentTimeMillis();
            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                long currentTime = System.currentTimeMillis();
                if (isCameraFree() && currentTime - rightKeyDownTime <= 1000) {
                    Intent intent = new Intent(this, RecordVideoService.class);
                    startService(intent);
                }
            }
        }
        if (Math.abs(powerDownTime - rightKeyDownTime) < 200) {
        }

    }

    class GlassesCallback extends IGlassesCallback.Stub {

        @Override
        public void onBtRead(int cmd, byte[] bytes) throws RemoteException {
            StringBuilder sb3 = new StringBuilder();
            for (byte b : bytes) {
                sb3.append(String.format("%02X ", b));
            }
            GlassesLog.i("onBtRead bytes:" + sb3.toString());
            switch (cmd) {
                case GlassesManager.GET_OR_SET_BLE_STATUS:
                    if (bytes[0] == GlassesManager.BT_ACCESSIBLE_DISCOVERABLE_AND_NOT_CONNECTABLE_VALUE) {
                        int values = bytes[1];
                        GlassesLog.i("status:" + values);
                    }
                    break;
                case GlassesManager.GET_MCU_VERSION:
                    String version = new String(bytes);
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.GET_MCU_VERSION, version.getBytes()));
                    break;
            }
        }

        @Override
        public void onAppRead(byte[] bytes) throws RemoteException {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02X ", b));
            }
            GlassesLog.d("onAppRead accept msg is:" + sb.toString());
            boolean firstGroup = false;

            if (bytes.length >= OTHER_LENGTH_WITH_CRC && bytes[0] == HEAD_BYTES[0] &&
                    bytes[1] == HEAD_BYTES[1] && bytes[2] == HEAD_BYTES[2] &&
                    (bytes[3] == HEAD_BYTES[3] || bytes[3] == HEAD_BYTES[3] - 1)) {
                // 头帧 判断3个字节的头，减少命中的概率
                firstGroup = true;
            }
            int size = 0;

            if (firstGroup) {
                onFilterData(bytes);
            }
        }
    }

    GlassesCallback callback = new GlassesCallback();

    private void registerReceiver() {
        stateReceiver = new StateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(stateReceiver, filter);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        GlassesLog.d("Service onDestroy");
        cpuRunWakeLock.release();
        if(orientationEventListener!=null){
            orientationEventListener.disable();
            orientationEventListener = null;
        }
        EventHelper.getInstance().unregister(this);
        unregisterReceiver(stateReceiver);
    }

    @Override
    public void listen(EventInfo info) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            onEventMainThread(info);
        } else {
            mHandler.post(() -> onEventMainThread(info));
        }
    }

    public void onEventMainThread(EventInfo event) {
        GlassesLog.d("onEventMainThread " + Integer.toHexString(event.getWhat()));
        switch (event.getWhat()) {
            case Constants.ACTION_STATE_CHANGED:
                Utils.wakeup(this);
                break;
            case Constants.WIFI_STATE_OPEN:
                break;
            case Constants.WIFI_STATE_OFF:
                break;
            case Constants.WIFI_SCAN_RESULT:
                GlassesLog.d("onEventMainThread  WIFI_SCAN_RESULT    " + attemptTimes);
                if (attemptTimes < 1) return;
                attemptTimes--;
                boolean enable = WifiMgr.getInstance().connectWifi(
                        SPUtils.getInstance(this).getString(WLAN_AP_SSID),
                        SPUtils.getInstance(this).getString(WLAN_AP_PWD),
                        (List<ScanResult>) event.getObject()
                );

                if (isConnectAp) {
                    attemptTimes = 0;
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "1".getBytes()));
                } else {
                    WifiMgr.getInstance().startScan();
                }
                break;
            case Constants.WIFI_CONNECT_DEVICE:
                String targetSsid = SPUtils.getInstance(this).getString(WLAN_AP_SSID);
                GlassesLog.d("WIFI_CONNECT_DEVICE ssid " + targetSsid);
                if (WifiMgr.getInstance().isWifiConnected(targetSsid)) {
                    isConnectAp = true;
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "1".getBytes()));
                } else {
                    isConnectAp = false;
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "0".getBytes()));
                }
                break;
            case Constants.WIFI_DISCONNECT:
                isConnectAp = false;
                GlassesLog.d("WIFI_DISCONNECT");
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "0".getBytes()));
                break;
            case TRANSFORM_PICTURE:
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(CmdType.CONNECT_WIFI_IP, ("" + WifiMgr.getInstance().getIpAddressFromHotspot()).getBytes()));
                GlassesLog.d("TRANSFORM_PICTURE send file");
                if (isConnectAp) {
                    sendFile(event.getInfo());
                }
                break;
            case Constants.CMD_TAKE_PHOTO:
                if (isConnectAp) {
                    Intent service = new Intent(this, TakePictrueService.class);
                    service.setPackage("com.wj.GlassesServer");
                    startService(service);
                }
                break;
            case Constants.CMD_START_OTA:
                String md5 = new String((byte[]) event.getObject());
                startOtaSocket(md5);
                GlassesLog.d("CMD_START_OTA call sendCmdToApp");
                mGlassesManager.sendCmd(mGlassesManager.processData(GlassesManager.START_OTA_SEND, Formatter.formatIpAddress(WifiMgr.getInstance().getConnectionInfo().getIpAddress()).getBytes()));
                break;
        }
    }

    private void onMsgStart(int msgId, byte[] data) {
        GlassesLog.d(TAG + "  onMsgStart OnMsgListener: <--" + Integer.toHexString(msgId));
        switch (msgId) {
            case CmdType.CONNECT_WIFI_IP:
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(CmdType.CONNECT_WIFI_IP, ("" + WifiMgr.getInstance().getIpAddressFromHotspot()).getBytes()));
                break;
            case GlassesManager.WIFI_CONNECT_STATE:
                String ssid = SPUtils.getInstance(this).getString(WLAN_AP_SSID);
                if (!TextUtils.isEmpty(ssid) && WifiMgr.getInstance().isWifiConnected(ssid)) {
                    isConnectAp = true;
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "1".getBytes()));
                } else {
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "0".getBytes()));
                }
                break;

            case GlassesManager.WIFI_CONNECT:
                WifiMgr.getInstance().openWifi();
                attemptTimes = 3;
                String wfData = new String(data);
                String[] info = wfData.split("::");
                GlassesLog.d("WIFI_CONNECT  " + info[0] + "  " + info[1]);

                SPUtils.getInstance(this).put(WLAN_AP_SSID, info[0]);
                SPUtils.getInstance(this).put(WLAN_AP_PWD, info[1]);
                if (WifiMgr.getInstance().isWifiConnected(info[0])) {
                    //已连接到指定ssid，通知客户端
                    isConnectAp = true;
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.WIFI_CONNECT_STATE, "1".getBytes()));
                } else {
                    //连接到指点ssid
                    isConnectAp = false;
                    WifiMgr.getInstance().startScan();
                }
                break;
            case GlassesManager.WIFI_RTSP_START:
                GlassesLog.d("WIFI_RTSP_START  ");
                MainActivity.wakeup(this);
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(CmdType.CONNECT_WIFI_IP, ("" + WifiMgr.getInstance().getIpAddressFromHotspot()).getBytes()));
                if (!GlassesApplication.isRtspForeground()) {
                    MainActivity.launch(this);
                } else {
                    Intent intent2 = new Intent();
                    intent2.setAction(MainActivity.ACTION_RTSP_START);
                    sendBroadcast(intent2);
                }
                break;
            case GlassesManager.WIFI_RTSP_STOP:
                GlassesLog.d("WIFI_RTSP_STOP  ");
                Intent intent3 = new Intent();
                intent3.setAction(MainActivity.ACTION_RTSP_STOP);
                sendBroadcast(intent3);
                break;
            case GlassesManager.WIFI_SOCKET_START:
                //服务端已开启
                break;
            case GlassesManager.WIFI_SOCKET_STOP:
                closeSocket();
                break;
            case GlassesManager.WIFI_SOCKET_CONNECT:
                break;
            case GlassesManager.SETTINGS_GET_VOLUME:
                String vmData = new String(data);
                if (TextUtils.isEmpty(vmData)) {
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.SETTINGS_GET_VOLUME, (mGlassesUtils.getVolume() + "").getBytes()));
                } else {
                    mGlassesManager.ajustVolumeValue(AudioManager.STREAM_MUSIC, Integer.valueOf(vmData));
                }
                break;
            case GlassesManager.TAKING_PICTURES:
                if (isConnectAp) {
                    Intent service = new Intent(this, TakePictrueService.class);
                    service.setPackage("com.wj.GlassesServer");
                    service.putExtra("is_self", false);
                    isSelfCall = false;
                    startService(service);
                }
                break;
            case GlassesManager.GETTING_BATTERY:
                int value = mGlassesUtils.getBatteryVolume();
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.GETTING_BATTERY, ("" + value).getBytes()));
                break;
            case GlassesManager.AJUST_VOLUME_VALUE:
                mGlassesManager.ajustVolumeValue(AudioManager.STREAM_MUSIC, 10);
                break;
            case GlassesManager.GET_GLASS_VERSION:
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.GET_GLASS_VERSION, (SystemProperties.get("ro.system.build.version.incremental")).getBytes()));
                break;
            case GlassesManager.START_OTA_SEND:
                String md5 = new String(data);
                startOtaSocket(md5);
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.START_OTA_SEND, Formatter.formatIpAddress(WifiMgr.getInstance().getConnectionInfo().getIpAddress()).getBytes()));
                break;
            case CmdType.GET_BT_ADDR:
                String btMac = SystemProperties.get("persist.sys.sim.bt_mcu_mac");
                GlassesLog.d("GET_BT_ADDR call sendCmdToApp");
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(CmdType.GET_BT_ADDR, btMac.getBytes()));
                break;
            case GlassesManager.GET_MCU_VERSION:
                mGlassesManager.sendCmd(mGlassesManager.processData(GlassesManager.GET_MCU_VERSION, "".getBytes()));
                break;
            case CmdType.END_PICTURE_RECEIVE:
                try {
                    if (mClientSocket != null)
                        mClientSocket.close();
                    if (file != null)
                        file.deleteOnExit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case CmdType.REQUEST_SEND_PHOTE:
                List<Media> photos = GlassesApplication.getApplication().getMediaByType(1);
                GlassesLog.d("wj007 REQUEST_SEND_PHOTE size->" + photos.size());
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(CmdType.REQUEST_SEND_PHOTE, ("" + photos.size()).getBytes()));
                sendPatchFile(photos);
                break;
            case CmdType.REQUEST_SEND_VIDEO:
                List<Media> videos = GlassesApplication.getApplication().getMediaByType(2);
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(CmdType.REQUEST_SEND_PHOTE, ("" + videos.size()).getBytes()));
                sendPatchFile(videos);
                break;
            default:
                break;
        }
    }

    private void sendPatchFile(List<Media> medias) {
        EXECUTOR.execute(() -> {
            DataOutputStream mOut = null;
            BufferedInputStream bis;
            try {
                GlassesLog.d("start sendPatchFile+++++");
                if (mClientSocket == null || mClientSocket.isClosed()) {
                    mClientSocket = new Socket(WifiMgr.getInstance().getIpAddressFromHotspot(), FILE_PORT);
                }
                GlassesLog.d(" start sendPatchFile file count:" + medias.size());
                mOut = new DataOutputStream(mClientSocket.getOutputStream());
                for (Media media : medias) {
                    GlassesLog.d("run start update media name:" + media.name + " path:" + media.path);
                    File file = new File(media.path);
                    if (!file.exists()) {
                        continue;
                    }
                    mOut.writeInt(medias.size()); //文件标记
                    mOut.writeUTF(file.getName()); //文件名
                    mOut.writeLong(file.length()); //文件长度
                    mOut.flush();
                    bis = new BufferedInputStream(new FileInputStream(media.path));
                    int byteRead;
                    byte[] buffer = new byte[2 * 1024];
                    while ((byteRead = bis.read(buffer)) != -1) {
                        mOut.write(buffer, 0, byteRead);
                        mOut.flush();
                    }
                    if (bis != null) {
                        bis.close();
                    }
                }

                if (mOut != null) {
                    mOut.close();
                }
                GlassesLog.d("end sendPatchFile----------");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }


    private void doReceive(byte[] data) {
        if (data[0] != HEAD_BYTES[0] || data[1] != HEAD_BYTES[1]) {
            return;
        }
        StringBuilder sb3 = new StringBuilder();
        for (byte b : data) {
            sb3.append(String.format("%02X ", b));
        }
        GlassesLog.d("doReceive: data -->  " + sb3.toString());
        final int msgId = BitUtil.bytesTo2IntBig(data, HEAD_BYTES.length);
        byte[] aa = new byte[data.length - OTHER_LENGTH];
        System.arraycopy(data, OTHER_LENGTH, aa, 0, aa.length);
        onMsgStart(msgId, aa);
    }

    private void onFilterData(byte[] paramArray) {
        if (paramArray[0] == HEAD_BYTES[0] && paramArray[1] == HEAD_BYTES[1] &&
                paramArray[2] == HEAD_BYTES[2] && paramArray[3] == HEAD_BYTES[3]) {
            int contentLength = BitUtil.bytesTo4IntBig(paramArray, HEAD_BYTES.length + MSG_ID_LENGTH + MSG_INDEX_LENGTH);
            GlassesLog.d("onFilterData contentLength->" + contentLength);
            byte totalData[] = new byte[contentLength + OTHER_LENGTH];
            System.arraycopy(paramArray, 0, totalData, 0, contentLength + OTHER_LENGTH);
            doReceive(totalData);
            if (paramArray.length == contentLength + OTHER_LENGTH_WITH_CRC || paramArray.length == contentLength + OTHER_LENGTH) {
                return;
            } else {
                byte temp[] = new byte[paramArray.length - contentLength - OTHER_LENGTH];
                System.arraycopy(paramArray, contentLength + OTHER_LENGTH, temp, 0, paramArray.length - (contentLength + OTHER_LENGTH));
                if (temp[0] == ((byte) 0xFF) && temp[1] == ((byte) 0xFF)) {
                    GlassesLog.d(" on onFilterData with cr");
                    temp = new byte[paramArray.length - contentLength - OTHER_LENGTH_WITH_CRC];
                    System.arraycopy(paramArray, contentLength + OTHER_LENGTH_WITH_CRC, temp, 0, paramArray.length - (contentLength + OTHER_LENGTH_WITH_CRC));
                }
                StringBuilder sd = new StringBuilder();
                for (byte b : temp) {
                    sd.append(String.format("%02X ", b));
                }
                GlassesLog.d("onFilterData call onFilterData data:" + sd.toString());
                onFilterData(temp);
            }
        }

    }

    private void closeSocket() {
        try {
            if (mOut != null) {
                mOut.close();
                mOut = null;
            }
            if (mClientSocket != null) {
                mClientSocket.close();
                mClientSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startOtaSocket(String md5) {
        EXECUTOR.execute(() -> {
            File tempFile = null;
            try {
                GlassesLog.d("duyi startOtaSocket+++++++");
                if (socketServer == null) {
                    try {
                        socketServer = new ServerSocket();
                        socketServer.bind(new InetSocketAddress(Formatter.formatIpAddress(WifiMgr.getInstance().getConnectionInfo().getIpAddress()), FILE_OTA_PORT));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                socketServer.setReuseAddress(true);
                Socket clientSocket = socketServer.accept();
                inputStream = clientSocket.getInputStream();
                File path = new File("data/ota_package");
                if (!path.exists() && path.isDirectory()) {
                    path.mkdir();
                }
                tempFile = new File(path, "fota.zip");
                String adbCommand = "chmod 777 data/ota_package/fota.zip";
                Process process = Runtime.getRuntime().exec(adbCommand);
                int status = 0;
                status = process.waitFor();
                tempFile.setWritable(true);
                tempFile.setReadable(true);
                if (!tempFile.exists()) {
                    tempFile.createNewFile();
                }
                GlassesLog.d("duyi status->" + status);
                if (status != 0) {
                    isComplete = false;
                } else {
                    FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                        fileOutputStream.flush();
                    }
                    fileOutputStream.close();
                    isComplete = true;
                    GlassesLog.d("duyi startOtaSocket start write------");
                }
                inputStream.close();
                clientSocket.close();
                inputStream = null;
                clientSocket = null;
            } catch (IOException e) {
//                BleManager.getInstance().write(CmdType.TASK_EXCUTE_FAILED,"".getBytes());
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.TASK_EXCUTE_FAILED, "".getBytes()));
                isComplete = false;
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (socketServer != null) {
                    try {
                        socketServer.close();
                        tempFile.deleteOnExit();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    socketServer = null;
                }

                if (isComplete) {
//                    BleManager.getInstance().write(CmdType.START_OTA_SEND_COMPLETE,"".getBytes());
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.START_OTA_SEND_COMPLETE, "".getBytes()));
                    Intent intent = new Intent(FOTA_DOWNLOAD_COMPLETE_ACTION);
                    intent.setPackage("com.simcom.update");
                    intent.putExtra("md5", md5);
                    sendBroadcast(intent);
                } else {
//                    BleManager.getInstance().write(CmdType.START_OTA_SEND_FAILED,"".getBytes());
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.START_OTA_SEND_FAILED, "".getBytes()));
                }
                GlassesLog.d("duyi startOtaSocket---------------");
            }
        });
    }


    /**
     * 发送文件
     */
    public void sendFile(String filePath) {
        GlassesLog.d("================== sendFile " + filePath);
        EXECUTOR.execute(() -> {
            try {
                if (mClientSocket == null || mClientSocket.isClosed()) {
                    mClientSocket = new Socket(WifiMgr.getInstance().getIpAddressFromHotspot(), FILE_PORT);
                }
                mOut = new DataOutputStream(mClientSocket.getOutputStream());
                FileInputStream in = new FileInputStream(filePath);
                file = new File(filePath);
                mOut.writeInt(FLAG_FILE); //文件标记
                mOut.writeUTF(file.getName()); //文件名
                mOut.writeLong(file.length()); //文件长度
                int r;
                byte[] b = new byte[4 * 1024];
                //"正在发送文件(" + file.getPath() + "),请稍后...");
                while ((r = in.read(b)) != -1) {
                    mOut.write(b, 0, r);
                }
                mOut.flush();
                mOut.close();
                in.close();

                GlassesLog.d("run: send file end " + file.length());
            } catch (Throwable e) {
//                BleManager.getInstance().write(CmdType.TASK_EXCUTE_FAILED,"".getBytes());
                mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.TASK_EXCUTE_FAILED, "".getBytes()));
                e.printStackTrace();
            }
        });
    }


    //前台通知
    private void showNotification() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
            builder.setContentTitle(getString(R.string.app_name))
                    .setWhen(System.currentTimeMillis())
                    .setContentText("")
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSmallIcon(R.mipmap.ic_launcher);

            String channel_name = getString(R.string.app_name);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channel_name,
                        NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                channel.setSound(null, null);
                channel.enableVibration(false);
                manager.createNotificationChannel(channel);
            }

            Notification notification = builder.build();
            startForeground(NOTIFICATION_SERVER_ID, notification);
        } catch (Exception e) {
            e.toString();
        }
    }

    /**
     * 取消显示常驻消息栏
     */
    public void stopNotification() {
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showNotification();
        return START_STICKY;
    }

    Handler mWearHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            GlassesLog.i("mWearHandler, msg.what=" + msg.what);
            GlassesLog.i("mWearHandler, getWearHandlerState=" + getWearHandlerState());
            try {
                switch (msg.what) {
                    case DISCONNECT1_BT_WHAT:
                        mGlassesManager.sendCmd(mGlassesManager.processData(GlassesManager.GET_OR_SET_BT_STATUS, GlassesManager.SetBTLimitDiscovAndConnBytes));
                        Message btMsg = Message.obtain();
                        btMsg.what = DISCONNECT2_BT_WHAT;
                        sendMessageDelayed(btMsg, DISCONNECT2_DELAY_TIME);
                        break;
                    case DISCONNECT2_BT_WHAT:
                        mGlassesManager.sendCmd(mGlassesManager.processData(GlassesManager.GET_OR_SET_BT_STATUS, GlassesManager.SetBTNoDiscovAndConnBytes));
                        if (WEAR_MODE_AUTO_SHUTDOWN == wearMode) {
                            Message ShutMsg = Message.obtain();
                            ShutMsg.what = SHUTDOWN_WHAT;
                            sendMessageDelayed(ShutMsg, SHUTDOWN_DELAY_TIME);
                        } else {
                            releaseWake();
                        }
                        break;
                    case RESET1_MCU_WHAT:
                        SystemProperties.set("sys.reset_mcu", "1");
                        Message mcuMsg = Message.obtain();
                        mcuMsg.what = RESET2_MCU_WHAT;
                        sendMessageDelayed(mcuMsg, RESETMCU_DELAY_TIME);
                        break;
                    case RESET2_MCU_WHAT:
                        SystemProperties.set("sys.reset_mcu", "0");
                        break;
                    case TURN_OFF_LED_WHAT:
                        LedNotification(-1);
                        break;
                    case SHUTDOWN_WHAT:
                        IPowerManager pm = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
                        pm.shutdown(false, "userrequestd", false);
                        return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            setWearHandlerState(msg.what);
        }
    };

    private void setWearHandlerState(int value) {
        if (TURN_OFF_LED_WHAT != value) {
            GlassesLog.i("setWearHandlerState, mWearHandlerState=" + value);
            mWearHandlerState = value;
        }
    }

    private int getWearHandlerState() {
        return mWearHandlerState;
    }

    private void removeAllWearTask() {
        try {
            mWearHandler.removeMessages(DISCONNECT1_BT_WHAT);
            mWearHandler.removeMessages(DISCONNECT2_BT_WHAT);
            mWearHandler.removeMessages(RESET1_MCU_WHAT);
            mWearHandler.removeMessages(RESET2_MCU_WHAT);
            mWearHandler.removeMessages(SHUTDOWN_WHAT);
            mWearHandler.removeMessages(TURN_OFF_LED_WHAT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean wearinFuction() {
        if (WEAR_MODE_NONE == wearMode) {
            return false;
        }
        removeAllWearTask();

        LedNotification(BLUE_BLINK_5_TIMES);

        GlassesLog.i("wearinFuction, getWearHandlerState=" + getWearHandlerState());
        if (0 != getWearHandlerState() && RESET2_MCU_WHAT != getWearHandlerState()) {
            try {
                Message mcuMsg = Message.obtain();
                mcuMsg.what = RESET1_MCU_WHAT;
                //mWearHandler.sendMessage(mcuMsg);
                mWearHandler.sendMessageDelayed(mcuMsg, LEDOFF_DELAY_TIME);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private boolean wearoutFunction() {
        if (WEAR_MODE_NONE == wearMode) {
            return false;
        }
        LedNotification(RED_BLINK_5_TIMES);

        try {
            Message disconnecMsg = Message.obtain();
            disconnecMsg.what = DISCONNECT1_BT_WHAT;
            mWearHandler.sendMessageDelayed(disconnecMsg, DISCONNECT1_DELAY_TIME);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
    private void initOrientationDetection(){

        orientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {

            @Override

            public void onOrientationChanged(int orientation) {

                GlassesLog.d("initOrientationDetection orientation:"+orientation);

                if(orientation == ORIENTATION_UNKNOWN)return;

                Constants.currDeviceOrientation = roundOrientation(orientation);

            }

        };

        if(orientationEventListener.canDetectOrientation()){

            orientationEventListener.enable();

        }

    }

    private int roundOrientation(int orientation){

        return (orientation+45)/90*90%360;

    }

    private void LedNotification(int type) {
        byte[] LedBytes = null;
        boolean validType = true;
        try {
            switch (type) {
                case RED_BLINK_5_TIMES:
                    LedBytes = new byte[]{0x00, (byte) 255, 0x02, 0x02, 0x05};
                    break;
                case BLUE_BLINK_5_TIMES:
                    LedBytes = new byte[]{0x02, (byte) 255, 0x02, 0x02, 0x05};
                    break;
                default:
                    validType = false;
                    LedBytes = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00};
                    break;
            }
            mGlassesManager.sendCmd(mGlassesManager.processMcuLedData(GlassesManager.SET_LED_STATUS, LedBytes, GlassesManager.LED_NOTIFICATION));
            if (validType) {
                Message mcuMsg = Message.obtain();
                mcuMsg.what = TURN_OFF_LED_WHAT;
                mWearHandler.sendMessageDelayed(mcuMsg, LEDOFF_DELAY_TIME);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}