package com.wj.glasses.utils;

import android.util.Base64;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.MessageDigest;

public class DigestUtil {
    @StringDef({MD5, SHA1, SHA256})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Algorithm {
    }

    public static final String MD5 = "MD5";
    public static final String SHA1 = "SHA-1";
    public static final String SHA256 = "SHA-256";

    public static byte[] getDigest(String text, @Algorithm String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(text.getBytes("UTF-8"));
            byte[] bytes = md.digest();
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getMD5(String str) {
        // 为了方便存储和http传输，encode特性用 Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE
        return new String(Base64.encode(getDigest(str, MD5), Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE));
    }
}
