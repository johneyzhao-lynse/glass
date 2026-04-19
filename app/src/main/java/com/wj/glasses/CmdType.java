package com.wj.glasses;

public interface CmdType {
    int GET_BT_ADDR = 0xBBBB;
    int CONNECT_WIFI_IP = 0xBBB1;
    int GLASSES_SELF_TAKE_PHOTO = 0xBBBC;
    int END_PICTURE_RECEIVE = 0xBBBD;
    int REQUEST_SEND_PHOTE = 0xB019;
    int REQUEST_SEND_VIDEO = 0xB020;
}
