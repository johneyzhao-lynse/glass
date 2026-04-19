package com.wj.glasses.utils;

/**
 * 字节处理工具类
 */
public class BitUtil {
    /**
     * 大端存储
     * @param num
     * @return
     */
    public static byte[] intToBytesBig(int num){
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (0xff & (num >> 24));
        bytes[1] = (byte) (0xff & (num >> 16));
        bytes[2] = (byte) (0xff & (num >> 8));
        bytes[3] = (byte) (0xff & num);
        return bytes;
    }

    public static void intTo2BytesBig(byte[] bytes, int num, int offset){
        bytes[offset] = (byte) (0xff & (num >> 8));
        bytes[offset + 1] = (byte) (0xff & num);
    }

    /**
     * 4字节 byte 转  int
     */
    public static int bytesTo4IntBig(byte[] src, int offset) {
        return (((src[offset] & 0xFF)<<24)
                |((src[offset+1] & 0xFF)<<16)
                |((src[offset+2] & 0xFF)<<8)
                |(src[offset+3] & 0xFF));
    }

    /**
     * int 转  4字节 byte
     */
    public static byte[] intTo4BytesBig(byte[] bytes, int num, int offset){
//        byte[] bytes = new byte[4];
        bytes[offset] = (byte) (0xff & (num >> 24));
        bytes[offset + 1] = (byte) (0xff & (num >> 16));
        bytes[offset + 2] = (byte) (0xff & (num >> 8));
        bytes[offset + 3] = (byte) (0xff & num);
        return bytes;
    }

    public static int bytesToIntBig(byte[] src) {
        return bytesToIntBig(src, 0);
    }

    public static int bytesToIntBig(byte[] src, int offset) {
        int value = (((src[offset] & 0xFF)<<24)
                |((src[offset+1] & 0xFF)<<16)
                |((src[offset+2] & 0xFF)<<8)
                |(src[offset+3] & 0xFF));
        return value;
    }

    public static int bytesTo2IntBig(byte[] src, int offset) {
        int value = (((src[offset] & 0xFF) << 8) | (src[offset + 1] & 0xFF));
        return value;
    }

    public static long bytesToLongBig(byte[] src, int offset) {
        long value = ((((long)src[offset] & 0xFF) << 24)
                |(((long)src[offset+1] & 0xFF)<<16)
                |(((long)src[offset+2] & 0xFF)<<8)
                |((long)src[offset+3] & 0xFF));
        return value;
    }

    public static byte[] longToBytesBig(byte[] bytes, long num, int offset){
        bytes[offset] = (byte) (0xff & (num >> 24));
        bytes[offset + 1] = (byte) (0xff & (num >> 16));
        bytes[offset + 2] = (byte) (0xff & (num >> 8));
        bytes[offset + 3] = (byte) (0xff & num);
        return bytes;
    }

    public static byte[] longToBytesBig2(byte[] bytes, long num, int offset){
        bytes[offset] = (byte) (0xff & (num >> 56));
        bytes[offset + 1] = (byte) (0xff & (num >> 48));
        bytes[offset + 2] = (byte) (0xff & (num >> 40));
        bytes[offset + 3] = (byte) (0xff & (num >> 32));

        bytes[offset + 4] = (byte) (0xff & (num >> 24));
        bytes[offset + 5] = (byte) (0xff & (num >> 16));
        bytes[offset + 6] = (byte) (0xff & (num >> 8));
        bytes[offset + 7] = (byte) (0xff & num);
        return bytes;
    }
}