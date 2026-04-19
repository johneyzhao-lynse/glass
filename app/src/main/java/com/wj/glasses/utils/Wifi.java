package com.wj.glasses.utils;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.Comparator;
import java.util.List;

public class Wifi {
    private static final String TAG = "Wifi Connecter";

    public static final int SECURITY_NONE = 0;
    public static final int SECURITY_WEP = 1;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_EAP = 3;
    public static final int SECURITY_OWE = 4;
    public static final int SECURITY_SAE = 5;
    public static final int SECURITY_EAP_SUITE_B = 6;
    public static final int SECURITY_EAP_WPA3_ENTERPRISE = 7;


    /**
     * Change the password of an existing configured network and connect to it
     *
     * @param wifiMgr
     * @param config
     * @param newPassword
     * @return
     */
    public static boolean changePasswordAndConnect(final WifiManager wifiMgr, final WifiConfiguration config, final String newPassword, final int numOpenNetworksKept) {
        setupSecurity(config, getApSecurity(config), newPassword);
        final int networkId = wifiMgr.updateNetwork(config);
        if (networkId == -1) {
            // Update failed.
            return false;
        }
        // Force the change to apply.
        wifiMgr.disconnect();
        return connectToConfiguredNetwork(wifiMgr, config, true);
    }

    /**
     * Configure a network, and connect to it.
     *
     * @param wifiMgr
     * @param scanResult
     * @param password   Password for secure network or is ignored.
     * @return
     */
    public static boolean connectToNewNetwork(final WifiManager wifiMgr, final ScanResult scanResult, final String password) {
        final int security = getApSecurity(scanResult);
        GlassesLog.d("connectToNewNetwork  security = " + security);
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = convertToQuotedString(scanResult.SSID);
        config.BSSID = scanResult.BSSID;
        setupSecurity(config, security, password);

        int id = wifiMgr.addNetwork(config);
        if (id == -1) {
            GlassesLog.d("connectWifi error id");
            return false;
        }

        if (wifiMgr.saveConfiguration()) {
            GlassesLog.d("connectWifi error save ");
            return false;
        }

        config = getWifiConfiguration(wifiMgr, config, security);
        if (config == null) {
            GlassesLog.d("connectWifi config null");
            return false;
        }
        return connectToConfiguredNetwork(wifiMgr, config, true);
    }

    /**
     * Connect to a configured network.
     *
     * @return
     */
    public static boolean connectToConfiguredNetwork(final WifiManager wifiMgr, WifiConfiguration config, boolean reassociate) {
        if (Build.VERSION.SDK_INT >= 23) {
            return connectToConfiguredNetworkV23(wifiMgr, config, reassociate);
        }
        final int security = getApSecurity(config);

        int oldPri = config.priority;
        // Make it the highest priority.
        int newPri = getMaxPriority(wifiMgr) + 1;
        if (newPri > MAX_PRIORITY) {
            newPri = shiftPriorityAndSave(wifiMgr);
            config = getWifiConfiguration(wifiMgr, config, security);
            if (config == null) {
                GlassesLog.d("connectWifi config null ");

                return false;
            }
        }

        // Set highest priority to this configured network
        config.priority = newPri;
        int networkId = wifiMgr.updateNetwork(config);
        if (networkId == -1) {
            GlassesLog.d("connectWifi networkId = -1 ");
            return false;
        }

        // Do not disable others
        if (!wifiMgr.enableNetwork(networkId, false)) {
            config.priority = oldPri;
            GlassesLog.d("connectWifi enableNetwork true");
            return false;
        }

        if (!wifiMgr.saveConfiguration()) {
            config.priority = oldPri;
            GlassesLog.d("connectWifi saveConfiguration true");
            return false;
        }

        // We have to retrieve the WifiConfiguration after save.
        config = getWifiConfiguration(wifiMgr, config, security);
        if (config == null) {
            GlassesLog.d("connectWifi config null ");
            return false;
        }

        // Disable others, but do not save.
        // Just to force the WifiManager to connect to it.
        if (!wifiMgr.enableNetwork(config.networkId, true)) {
            GlassesLog.d("connectWifi enableNetwork true");
            return false;
        }

        final boolean connect = reassociate ? wifiMgr.reassociate() : wifiMgr.reconnect();
        if (!connect) {
            GlassesLog.d("connectWifi connect  true ");
            return false;
        }

        return true;
    }

    private static boolean connectToConfiguredNetworkV23(final WifiManager wifiMgr, WifiConfiguration config, boolean reassociate) {
        if (!wifiMgr.enableNetwork(config.networkId, true)) {
            return false;
        }

        return reassociate ? wifiMgr.reassociate() : wifiMgr.reconnect();
    }

    private static void sortByPriority(final List<WifiConfiguration> configurations) {
        java.util.Collections.sort(configurations, new Comparator<WifiConfiguration>() {

            @Override
            public int compare(WifiConfiguration object1,
                               WifiConfiguration object2) {
                return object1.priority - object2.priority;
            }
        });
    }

//    /**
//     * Ensure no more than numOpenNetworksKept open networks in configuration list.
//     * @param wifiMgr
//     * @param numOpenNetworksKept
//     * @return Operation succeed or not.
//     */
//    private static boolean checkForExcessOpenNetworkAndSave(final WifiManager wifiMgr, final int numOpenNetworksKept) {
//        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();
//        sortByPriority(configurations);
//
//        boolean modified = false;
//        int tempCount = 0;
//        for(int i = configurations.size() - 1; i >= 0; i--) {
//            final WifiConfiguration config = configurations.get(i);
//            if(SECURITY_NONE == getSecurity(config)) {
//                tempCount++;
//                if(tempCount >= numOpenNetworksKept) {
//                    modified = true;
//                    wifiMgr.removeNetwork(config.networkId);
//                }
//            }
//        }
//        if(modified) {
//            return wifiMgr.saveConfiguration();
//        }
//
//        return true;
//    }

    private static final int MAX_PRIORITY = 99999;

    private static int shiftPriorityAndSave(final WifiManager wifiMgr) {
        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();
        sortByPriority(configurations);
        final int size = configurations.size();
        for (int i = 0; i < size; i++) {
            final WifiConfiguration config = configurations.get(i);
            config.priority = i;
            wifiMgr.updateNetwork(config);
        }
        wifiMgr.saveConfiguration();
        return size;
    }

    private static int getMaxPriority(final WifiManager wifiManager) {
        final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        int pri = 0;
        for (final WifiConfiguration config : configurations) {
            if (config.priority > pri) {
                pri = config.priority;
            }
        }
        return pri;
    }

    private static final String BSSID_ANY = "any";

    public static WifiConfiguration getWifiConfiguration(final WifiManager wifiMgr, final ScanResult scanResult) {
        final String ssid = convertToQuotedString(scanResult.SSID);
        if (ssid.length() == 0) {
            return null;
        }

        final String bssid = scanResult.BSSID;
        if (bssid == null) {
            return null;
        }

        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();
        if (configurations == null) {
            return null;
        }

        for (final WifiConfiguration config : configurations) {
            if (config.SSID == null || !ssid.equals(config.SSID)) {
                continue;
            }
            if (config.BSSID == null || BSSID_ANY.equals(config.BSSID) || bssid.equals(config.BSSID)) {
                return config;
            }

        }
        return null;
    }

    public static WifiConfiguration getWifiConfiguration(final WifiManager wifiMgr, final WifiConfiguration configToFind, int security) {
        final String ssid = configToFind.SSID;
        if (ssid.length() == 0) {
            return null;
        }

        final String bssid = configToFind.BSSID;


        if (security == 0) {
            security = getApSecurity(configToFind);
        }

        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();

        for (final WifiConfiguration config : configurations) {
            if (config.SSID == null || !ssid.equals(config.SSID)) {
                continue;
            }
            if (config.BSSID == null || BSSID_ANY.equals(config.BSSID) || bssid == null || bssid.equals(config.BSSID)) {
                if (security == getApSecurity(config)) {
                    return config;
                }
            }
        }
        return null;
    }

    public static String convertToQuotedString(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }

        final int lastPos = string.length() - 1;
        if (lastPos > 0 && (string.charAt(0) == '"' && string.charAt(lastPos) == '"')) {
            return string;
        }

        return "\"" + string + "\"";
    }

    public static void setupSecurity(WifiConfiguration config, int security, String password) {
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();

        final int passwordLen = password == null ? 0 : password.length();
        switch (security) {
            case SECURITY_NONE:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;

            case SECURITY_WEP:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                if (passwordLen != 0) {
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((passwordLen == 10 || passwordLen == 26 || passwordLen == 58) &&
                            password.matches("[0-9A-Fa-f]*")) {
                        config.wepKeys[0] = password;
                    } else {
                        config.wepKeys[0] = '"' + password + '"';
                    }
                }
                break;

            case SECURITY_PSK:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                if (passwordLen != 0) {
                    if (password.matches("[0-9A-Fa-f]{64}")) {
                        config.preSharedKey = password;
                    } else {
                        config.preSharedKey = '"' + password + '"';
                    }
                }
                break;

            case SECURITY_EAP:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
                break;

            case SECURITY_SAE:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                config.preSharedKey = '"' + password + '"';
                break;
            default:
                Log.e(TAG, "Invalid security type: " + security);
        }
    }

    public static int getApSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
            return SECURITY_PSK;
        }
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
            return SECURITY_EAP;
        }
        return (config.wepKeys[0] != null) ? SECURITY_WEP : SECURITY_NONE;
    }

    public static int getApSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        } else if (result.capabilities.contains("OWE")) {
            return SECURITY_OWE;
        } else if (result.capabilities.contains("SAE")) {
            return SECURITY_SAE;
        } else if (result.capabilities.contains("EAP_SUITE_B_192")) {
            return SECURITY_EAP_SUITE_B;
        }
        return SECURITY_NONE;
    }
}