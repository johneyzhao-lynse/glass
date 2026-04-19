package com.wj.glasses.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import java.lang.reflect.Method;

public class GlassesUtils {
    public Context context;
    public GlassesUtils(Context context){
        this.context = context;
    }
    public void adjustVolume(int direction){
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
    }
    public void setVolume(Context context, int volume) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // 设置声音模式为STREAM_MUSIC(或其他你需要的模式)
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 将音量调整为指定的音量，但不发出提示音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
    }
    public void connectWifi(String ssid,String password){
        if(TextUtils.isEmpty(ssid)){
            Toast.makeText(context,"ssid不能为空",Toast.LENGTH_SHORT).show();
            return;
        }
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);


        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + "你的网络SSID" + "\""; // 将SSID替换为你要连接的网络名
        if(!TextUtils.isEmpty(password)){
            wifiConfig.preSharedKey = "\"" + "你的网络密码" + "\""; // 将密码替换为你要连接的网络密码
        }
        int networkId = wifiManager.addNetwork(wifiConfig);
        wifiManager.disconnect();
        boolean state = wifiManager.enableNetwork(networkId, true);
        state = wifiManager.reconnect();
        if(state == false){
            Toast.makeText(context,"wifi连接失败",Toast.LENGTH_SHORT).show();
        }
    }
    public int getBatteryVolume(){
        BatteryManager mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int value = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return value;
    }
    public String getGlassesVersion(){
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method getMethod = systemProperties.getMethod("get", String.class);
            return (String) getMethod.invoke(null, "ro.build.version.incremental");
        } catch (Exception e) {
            return "Unsupported";
        }
    }
    public String getDeviceInfo(){
        StringBuffer sb = new StringBuffer();
        sb.append(Build.VERSION.RELEASE);
        sb.append(",");
        String serialNumber = null;
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method getMethod = systemPropertiesClass.getMethod("get", String.class);
            serialNumber = (String) getMethod.invoke(systemPropertiesClass, "ro.serialno");
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append(serialNumber);
        sb.append(",");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String bluetoothAddress;
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            // 获取本机蓝牙地址
            bluetoothAddress = bluetoothAdapter.getAddress();
            // 输出蓝牙地址
            sb.append(bluetoothAddress);
        } else {
            System.out.println("Bluetooth is not enabled or not available.");
        }
        return sb.toString();
    }
    public int getVolume(){
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
}