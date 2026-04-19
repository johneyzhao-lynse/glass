package com.wj.glasses.utils;

public class EventInfo {
    private int what;//消息id
    private Object object;
    private String info;

    public EventInfo(){}

    public EventInfo(int what){
        this.what = what;
    }

    public EventInfo(int what, Object object) {
        this.what = what;
        this.object = object;
    }

    public int getWhat() {
        return what;
    }

    public Object getObject() {
        return object;
    }

    public void setWhat(int what) {
        this.what = what;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getInfo() {
        return info;
    }
}
