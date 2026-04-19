package com.wj.glasses.receive;

import static com.wj.glasses.GlassesServerService.WLAN_AP_SSID;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.glasses.GlassesManager;
import android.provider.ContactsContract;
import android.providers.settings.SystemSettingsProto;

import com.wj.glasses.GlassesApplication;
import com.wj.glasses.utils.BitUtil;
import com.wj.glasses.utils.EventInfo;
import com.wj.glasses.utils.GlassesLog;
import com.wj.glasses.utils.Constants;
import com.wj.glasses.utils.EventHelper;
import com.wj.glasses.utils.SPUtils;
import com.wj.glasses.utils.WifiMgr;

import java.util.List;

public class StateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                    EventHelper.getInstance().post(new EventInfo(Constants.WIFI_STATE_OPEN));
                } else if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                    EventHelper.getInstance().post(new EventInfo(Constants.WIFI_STATE_OFF));
                }
                break;

            case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                List<ScanResult> scanResults = WifiMgr.getInstance().getScanResults();
                if (scanResults.size() > 0) {
                    if (!WifiMgr.getInstance().isWifiConnected(SPUtils.getInstance(context).getString(WLAN_AP_SSID))) {
                        GlassesLog.i("scanResults.size() " + scanResults.size());
                        EventHelper.getInstance().post(new EventInfo(Constants.WIFI_SCAN_RESULT, WifiMgr.getInstance().filterScanResult(scanResults)));
                    }
                }
                break;

            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info != null) {
                    if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                        EventHelper.getInstance().post(new EventInfo(Constants.WIFI_CONNECT_DEVICE));
                    } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                        EventHelper.getInstance().post(new EventInfo(Constants.WIFI_DISCONNECT));
                    }
                }
                break;

            case Intent.ACTION_BATTERY_CHANGED:
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int batteryPct = (int) (level / (float) scale * 100);
                if (batteryPct != Constants.mLastBattery) {
                    GlassesManager mGlassesManager = (GlassesManager) context.getSystemService(Context.GLASSES_SERVICE);
                    mGlassesManager.sendCmdToApp(mGlassesManager.processData(GlassesManager.GETTING_BATTERY, ("" + batteryPct).getBytes()));
                    Constants.mLastBattery = batteryPct;
                }
                break;
            case Constants.ACTION_START_OTA:
                String md5 = intent.getStringExtra("md5");
                EventInfo msg = new EventInfo(Constants.CMD_START_OTA);
                msg.setInfo(md5);
                EventHelper.getInstance().post(msg);
                break;
            case Constants.ACTION_TAKE_PHOTO:
                EventHelper.getInstance().post(new EventInfo(Constants.CMD_TAKE_PHOTO));
                break;

        }
    }
}
