package com.wj.glasses.utils;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.wj.glasses.GlassesApplication;

import java.util.ArrayList;
import java.util.List;

public class WifiMgr {
    //过滤免密码连接的WiFi
    public static final String NO_PASSWORD = "[ESS]";
    public static final String NO_PASSWORD_WPS = "[WPS][ESS]";

    private Context mContext;
    private WifiManager wifiMgr;
    private ConnectivityManager conMgr;


    public WifiMgr() {
        wifiMgr = (WifiManager) GlassesApplication.getApplication().getSystemService(Context.WIFI_SERVICE);
        conMgr = (ConnectivityManager) GlassesApplication.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static WifiMgr getInstance() {
        return SingletonInstance.INSTANCE;
    }

    private static class SingletonInstance {
        private static final WifiMgr INSTANCE = new WifiMgr();
    }


    /**
     * 打开Wi-Fi
     */
    public void openWifi() {
        if (!wifiMgr.isWifiEnabled()) {
            wifiMgr.setWifiEnabled(true);
        }
    }

    /**
     * 关闭Wi-Fi
     */
    public void closeWifi() {
        if (wifiMgr.isWifiEnabled()) {
            wifiMgr.setWifiEnabled(false);
        }
    }

    /**
     * 当前WiFi是否开启
     */
    public boolean isWifiEnabled() {
        return wifiMgr.isWifiEnabled();
    }


    /**
     * 移除保存的WiFi信息
     */
    private void removeNetwork() {
        try {
            List<WifiConfiguration> existingConfigs = wifiMgr.getConfiguredNetworks();
            for (WifiConfiguration existingConfig : existingConfigs) {
                wifiMgr.removeNetwork(existingConfig.networkId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean addNetwork(String ssid, String pwd) {
        WifiConfiguration config = createWifiInfo(ssid, pwd, Wifi.SECURITY_PSK);
        int id = wifiMgr.addNetwork(config);
        wifiMgr.startScan();
        boolean enable = wifiMgr.enableNetwork(id, true);
        return enable;
    }

    /**
     * 获取当前连接WIFI的SSID
     */
    public String getSSID() {
        if (conMgr != null) {
            try {
                NetworkInfo networkInfo = conMgr.getActiveNetworkInfo();
                if (networkInfo != null) {
                    String s = networkInfo.getExtraInfo();
                    if (s.length() > 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                        return s.substring(1, s.length() - 1);
                    }
                }
            } catch (Exception e) {
                System.err.println("getSSID failed: " + e);
                e.printStackTrace();
            }
        }
        return null;
    }

    public WifiConfiguration createWifiInfo(String SSID, String password, int type) {
        WifiConfiguration cf = new WifiConfiguration();

        WifiConfiguration config2 = new WifiConfiguration();
        config2.allowedAuthAlgorithms.clear();
        config2.allowedGroupCiphers.clear();
        config2.allowedKeyManagement.clear();
        config2.allowedPairwiseCiphers.clear();
        config2.allowedProtocols.clear();
        config2.SSID = "\"" + SSID + "\"";
        switch (type) {
            //none
            case Wifi.SECURITY_NONE:
                config2.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
            case Wifi.SECURITY_WEP:
                cf.SSID = "\"" + SSID + "\"";
                cf.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);
                if (password.length() != 0) {
                    int length = password.length();
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((length == 10 || length == 26 || length == 58)
                            && password.matches("[0-9A-Fa-f]*")) {
                        cf.wepKeys[0] = password;
                    } else {
                        cf.wepKeys[0] = '"' + password + '"';
                    }
                }
                return cf;
            case Wifi.SECURITY_PSK:
                if (password.matches("[0-9A-Fa-f]{64}")) {
                    config2.preSharedKey = password;
                } else {
                    config2.preSharedKey = '"' + password + '"';
                }
                config2.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config2.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config2.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config2.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config2.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config2.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config2.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                config2.status = WifiConfiguration.Status.ENABLED;
                break;
            default:
                return null;
        }
        return config2;
    }


    /**
     * 清除指定网络
     *
     * @param SSID
     */
    public void removeNetwork(String SSID) {
        WifiConfiguration tempConfig = isExsits(SSID);
        if (tempConfig != null) {
            wifiMgr.removeNetwork(tempConfig.networkId);
        }
    }

    /**
     * 判断当前网络是否WiFi
     *
     * @param context
     * @return
     */
    public boolean isWifi(final Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.getType() == 1;
    }

    /**
     * 扫描周围可用WiFi
     *
     * @return
     */
    public boolean startScan() {
        if (isWifiEnabled()) {
            return wifiMgr.startScan();
        }
        return false;
    }

    /**
     * 获取周围可用WiFi扫描结果
     *
     * @return
     */
    public List<ScanResult> getScanResults() {
        List<ScanResult> scanResults = wifiMgr.getScanResults();
        if (scanResults != null && scanResults.size() > 0) {
            return filterScanResult(scanResults);
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 获取周围的WiFi列表
     *
     * @return
     * @throws InterruptedException
     */
    public List<ScanResult> getWifiScanList() throws InterruptedException {
        List<ScanResult> resList = new ArrayList<ScanResult>();
        if (wifiMgr.startScan()) {
            List<ScanResult> tmpList = wifiMgr.getScanResults();
            Thread.sleep(2000);
            if (tmpList != null && tmpList.size() > 0) {
//				resList = sortByLevel(tmpList);
                for (ScanResult scanResult : tmpList) {
                    resList.add(scanResult);

                }
            } else {
                System.err.println("扫描为空");
            }
        }
        return resList;
    }

    /**
     * 判断当前WiFi是否正确连接指定WiFi
     *
     * @param SSID
     * @return
     */
    public boolean isWifiConnected(String SSID) {
        return SSID.equals(getConnectedSSID());
    }

    /**
     * 获取当前连接WiFi的SSID
     *
     * @return
     */
    public String getConnectedSSID() {
        WifiInfo wifiInfo = getConnectionInfo();
        if (wifiInfo == null) {
            return null;
        }
        switch (wifiInfo.getSupplicantState()) {
            case COMPLETED:
                return wifiInfo.getSSID().replace("\"", "");
            case AUTHENTICATING:
            case ASSOCIATING:
            case ASSOCIATED:
            case FOUR_WAY_HANDSHAKE:
            case GROUP_HANDSHAKE:
            default:
                return null;
        }
    }

    /**
     * 连接WiFi
     *
     * @param ssid
     * @param pwd
     * @param scanResults
     * @return
     * @throws InterruptedException
     */
    public boolean connectWifi(final String ssid, final String pwd, List<ScanResult> scanResults) {
        GlassesLog.d("connectWifi");
        if (scanResults == null || scanResults.size() == 0) {
            return false;
        }

        //匹配SSID相同的WiFi
        ScanResult result = null;
        for (ScanResult tmpResult : scanResults) {
            if (tmpResult.SSID.equals(ssid)) {
                GlassesLog.d("connectWifi: match to SSID");
                result = tmpResult;
                break;
            }
        }

        if (result == null) {
            GlassesLog.d("connectWifi: result is null");
            return false;
        }

        if (isAdHoc(result)) {
            GlassesLog.d("connectWifi: isAdHoc");
            return false;
        }

        return Wifi.connectToNewNetwork(wifiMgr, result, pwd);

/*
        final WifiConfiguration config = Wifi.getWifiConfiguration(wifiMgr, result);

        if (config == null) {
            //连接新WiFi
            boolean connResult;
            connResult = Wifi.connectToNewNetwork(wifiMgr, result, pwd);
            GlassesLog.d("connectWifi connect new wifi ");

            return connResult;
        } else {
            final boolean isCurrentNetwork_ConfigurationStatus = config.status == WifiConfiguration.Status.CURRENT;
            final WifiInfo info = getConnectionInfo();
            final boolean isCurrentNetwork_WifiInfo = info != null
                    && TextUtils.equals(info.getSSID(), result.SSID)
                    && TextUtils.equals(info.getBSSID(), result.BSSID);
            if (!isCurrentNetwork_ConfigurationStatus && !isCurrentNetwork_WifiInfo) {
                //连接已保存的WiFi
                final WifiConfiguration wcg = Wifi.getWifiConfiguration(wifiMgr, result);
                boolean connResult = false;
                if (wcg != null) {
                    connResult = Wifi.connectToConfiguredNetwork(wifiMgr, wcg, false);
                }
                GlassesLog.d("connectWifi 33333 ");

                return connResult;
            } else {
                //点击的是当前已连接的WiFi
                return true;
            }
        }
*/
    }

    /**
     * 断开指定ID的网络
     *
     * @param SSID
     */
    public boolean disconnectWifi(String SSID) {
        return wifiMgr.disableNetwork(getNetworkIdBySSID(SSID)) && wifiMgr.disconnect();
    }

    /**
     * 清除指定SSID的网络
     *
     * @param SSID
     */
    public void clearWifiConfig(String SSID) {
        SSID = SSID.replace("\"", "");
        List<WifiConfiguration> wifiConfigurations = wifiMgr.getConfiguredNetworks();
        if (wifiConfigurations != null && wifiConfigurations.size() > 0) {
            for (WifiConfiguration wifiConfiguration : wifiConfigurations) {
                if (wifiConfiguration.SSID.replace("\"", "").contains(SSID)) {
                    wifiMgr.removeNetwork(wifiConfiguration.networkId);
                    wifiMgr.saveConfiguration();
                }
            }
        }
    }

    /**
     * 清除当前连接的WiFi网络
     */
    public void clearWifiConfig() {
        String SSID = getConnectedSSID();
        List<WifiConfiguration> wifiConfigurations = wifiMgr.getConfiguredNetworks();
        if (wifiConfigurations != null && wifiConfigurations.size() > 0) {
            for (WifiConfiguration wifiConfiguration : wifiConfigurations) {
                if (wifiConfiguration.SSID.replace("\"", "").contains(SSID)) {
                    wifiMgr.removeNetwork(wifiConfiguration.networkId);
                    wifiMgr.saveConfiguration();
                }
            }
        }
    }

    private boolean isAdHoc(final ScanResult scanResult) {
        return scanResult.capabilities.indexOf("IBSS") != -1;
    }

    /**
     * 根据SSID查networkID
     *
     * @param SSID
     * @return
     */
    public int getNetworkIdBySSID(String SSID) {
        if (TextUtils.isEmpty(SSID)) {
            return 0;
        }
        WifiConfiguration config = isExsits(SSID);
        if (config != null) {
            return config.networkId;
        }
        return 0;
    }

    /**
     * 获取连接WiFi后的IP地址
     *
     * @return
     */
    public String getIpAddressFromHotspot() {
        DhcpInfo dhcpInfo = wifiMgr.getDhcpInfo();
        if (dhcpInfo != null) {
            int address = dhcpInfo.gateway;
            return ((address & 0xFF)
                    + "." + ((address >> 8) & 0xFF)
                    + "." + ((address >> 16) & 0xFF)
                    + "." + ((address >> 24) & 0xFF));
        }
        return null;
    }

    /**
     * 创建WifiConfiguration对象 分为三种情况：1没有密码;2用wep加密;3用wpa加密
     *
     * @param SSID
     * @param Password
     * @param Type
     * @return
     */
    public WifiConfiguration CreateWifiInfo(String SSID, String Password,
                                            int Type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + SSID + "\"";

        WifiConfiguration tempConfig = isExsits(SSID);
        if (tempConfig != null) {
            wifiMgr.removeNetwork(tempConfig.networkId);
        }

        if (Type == 1) // WIFICIPHER_NOPASS
        {
            config.wepKeys[0] = "";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        }
        if (Type == 2) // WIFICIPHER_WEP
        {
            config.hiddenSSID = true;
            config.wepKeys[0] = "\"" + Password + "\"";
            config.allowedAuthAlgorithms
                    .set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers
                    .set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        }
        if (Type == 3) // WIFICIPHER_WPA
        {
            config.preSharedKey = "\"" + Password + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms
                    .set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers
                    .set(WifiConfiguration.PairwiseCipher.TKIP);
            // config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers
                    .set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }
        return config;
    }

    /**
     * 添加一个网络并连接 传入参数：WIFI发生配置类WifiConfiguration
     */
    public boolean addNetwork(WifiConfiguration wcg) {
        int wcgID = wifiMgr.addNetwork(wcg);
        return wifiMgr.enableNetwork(wcgID, true);
    }

    /**
     * 获取当前手机所连接的wifi信息
     */
    public WifiInfo getConnectionInfo() {
        return wifiMgr.getConnectionInfo();
    }

    /**
     * 获取指定WiFi信息
     *
     * @param SSID
     * @return
     */
    private WifiConfiguration isExsits(String SSID) {
        List<WifiConfiguration> existingConfigs = wifiMgr.getConfiguredNetworks();
        if (existingConfigs != null && existingConfigs.size() > 0) {
            for (WifiConfiguration existingConfig : existingConfigs) {
                if (existingConfig.SSID.equals(SSID) || existingConfig.SSID.equals("\"" + SSID + "\"")) {
                    return existingConfig;
                }
            }
        }
        return null;
    }

    /**
     * 过滤WiFi扫描结果
     *
     * @return
     */
    public List<ScanResult> filterScanResult(List<ScanResult> scanResults) {
        List<ScanResult> result = new ArrayList<>();
        if (scanResults == null) {
            return result;
        }

        for (ScanResult scanResult : scanResults) {
            if (!TextUtils.isEmpty(scanResult.SSID) && scanResult.level > -80) {
                result.add(scanResult);
            }
        }

        for (int i = 0; i < result.size(); i++) {
            for (int j = 0; j < result.size(); j++) {
                //将搜索到的wifi根据信号强度从强到弱进行排序
                if (result.get(i).level > result.get(j).level) {
                    ScanResult temp = result.get(i);
                    result.set(i, result.get(j));
                    result.set(j, temp);
                }
            }
        }
        return result;
    }
}
