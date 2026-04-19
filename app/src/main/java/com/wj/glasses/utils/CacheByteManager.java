package com.wj.glasses.utils;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * 做部分缓存处理
 */
public class CacheByteManager {
    // 缓存20字节的长度
    private static final LinkedBlockingDeque<byte[]> mCacheBytes = new LinkedBlockingDeque<>();
    private static final int TWENTY_LENGTH = 20;
    private static int cacheLength = TWENTY_LENGTH;

    public static byte[] getBytes() {
        if (!mCacheBytes.isEmpty()) {
            mCacheBytes.poll();
        }

        return new byte[cacheLength];
    }

    public static void cache(byte[] bytes) {
        if (bytes == null) {
            return;
        }

        if (bytes.length == cacheLength) {
            mCacheBytes.add(bytes);
        }
    }

    public static void setCacheLength(int cacheLength) {
        if (cacheLength != CacheByteManager.cacheLength) {
            mCacheBytes.clear();
        }
        CacheByteManager.cacheLength = cacheLength;
    }
}
