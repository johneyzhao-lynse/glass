package com.wj.glasses.utils;


import java.util.LinkedList;

public class EventHelper {
    private static class EventHelperHolder {
        private static final EventHelper sInstance = new EventHelper();
    }

    private LinkedList<CallBack> mList = new LinkedList<>();

    private EventHelper(){}

    public static EventHelper getInstance() {
        return EventHelperHolder.sInstance;
    }

    public void register(CallBack callBack) {
        if (callBack == null) {
            return;
        }
        synchronized (mList) {
            mList.add(callBack);
        }
    }

    public void unregister(CallBack callBack) {
        if (callBack == null) {
            return;
        }
        synchronized (mList) {
            mList.remove(callBack);
        }
    }

    public void post(EventInfo info) {
        synchronized (mList) {
            for (CallBack callBack : mList) {
                callBack.listen(info);
            }
        }
    }

    public interface CallBack {
        public void listen(EventInfo info);
    }
}
