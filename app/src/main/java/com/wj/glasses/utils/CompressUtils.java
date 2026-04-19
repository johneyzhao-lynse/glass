package com.wj.glasses.utils;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CompressUtils {
    // 手表分辨率 320 * 360
    private static final int TARGET_WIDTH = 320;
    private static final int TARGET_HEIGHT = 320;

    //图片大小50K
    private static final int TARGET_SIZE = 50;

    public static Bitmap compress(String filePath, boolean compressQuality) {
        Bitmap bitmap = compressSize(filePath, TARGET_WIDTH, TARGET_HEIGHT);

        if(compressQuality){
            return compressQuality(bitmap, TARGET_SIZE);
        } else {
            return bitmap;
        }
    }

    public static Bitmap compress(String filePath) {
        Bitmap bitmap = compressSize(filePath, TARGET_WIDTH, TARGET_HEIGHT);
        if (bitmap != null) {
            return bitmap;
        }
        return compressQuality(bitmap, TARGET_SIZE);
    }

    public static Bitmap compress(String filePath, int targetWidth, int targetHeight) {
        Bitmap bitmap = compressSize(filePath, targetWidth, targetHeight);
        if (bitmap != null) {
            return bitmap;
        }
        return null;
    }

    public static byte[] compressToStream(String filePath) {
        File file = new File(filePath);
        if (file != null && file.exists()) {
            int size = (int) (file.length() / 1024);
            if (size > TARGET_SIZE) {
                Bitmap bitmap = compressSize(filePath, TARGET_WIDTH, TARGET_HEIGHT);
                return compressQualityToStream(bitmap, TARGET_SIZE);
            } else {
                return fileToByteArray(file);
            }
        }
        return null;
    }

    /**
     * 压缩清晰度
     *
     * @param image
     * @param targetSize
     * @return
     */
    public static Bitmap compressQuality(Bitmap image, int targetSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int options = 100;
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        while (baos.toByteArray().length / 1024 > targetSize) {
            baos.reset();
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
            options -= 10;
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);
        return bitmap;
    }

    /**
     * 压缩清晰度
     *
     * @param image
     * @param targetSize
     * @return
     */
    public static byte[] compressQualityToStream(Bitmap image, int targetSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int options = 100;
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        while (baos.toByteArray().length / 1024 > targetSize) {
            baos.reset();
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
            options -= 2;

            if(options<=0){
                break;
            }
        }
        return baos.toByteArray();
    }

    /**
     * 压缩大小
     *
     * @param filePath
     * @return
     */
    public static Bitmap compressSize(String filePath, int targetWidth, int targetHeight) {
        return compressSize(filePath, calculateRatio(filePath, targetWidth, targetHeight));
    }

    public static Bitmap compressSize(String filePath, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(filePath, options);
    }

    /**
     * 计算缩放比例
     *
     * @param filePath
     * @param width
     * @param height
     * @return
     */
    public static int calculateRatio(String filePath, int width, int height) {

        int ratio = 1;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(filePath, options);
        options.inJustDecodeBounds = false;

        int bitmapWidth = options.outWidth;
        int bitmapHeight = options.outHeight;

        float wRadio = bitmapWidth / (float)width;
        float hRadio = bitmapHeight / (float)height;
        float r = Math.max(wRadio, hRadio);
        while (r > 1) {
            ratio *= 2;
            r /= 2.0f;
        }
//        ratio = (bitmapWidth / width + bitmapHeight / height) / 2;
//        ratio = (ratio <= 0 ? 1 : ratio);

        return ratio;
    }

    private static byte[] fileToByteArray(File file){
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(file);
            int len;
            byte[] buffer = new byte[1024];
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bos.flush();
            bos.close();
            return bos.toByteArray();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static int getBitmapDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    public static void rotateBitmap(String filePath){
        Bitmap bitmap = compress(filePath,false);

        if(bitmap!=null){
            int degree = getBitmapDegree(filePath);
            if(degree>0){
                Matrix matrix = new Matrix();
                matrix.postRotate(degree);
                Bitmap bitmap1 = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false);
                if(bitmap1!=null){
                    saveBitmap(filePath,bitmap1, 90);
                }
            }
        }

        if(bitmap!=null && !bitmap.isRecycled()){
            bitmap.recycle();
        }
    }

    public static BitmapFactory.Options getBitmapOptions(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;//这个参数设置为true才有效，
        BitmapFactory.decodeFile(path, options);

        return options;
    }

    public static void getBitmapOptions(String path, BitmapFactory.Options options) {
        BitmapFactory.decodeFile(path, options);
    }

    public static void saveBitmap(String path, Bitmap bitmap, int quality, boolean canRecycle){
        File file = new File(path);

        if(file.exists()){
            file.delete();
        }

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(bitmap!=null && !bitmap.isRecycled() && file.exists()){
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG,quality,fos);
                fos.flush();
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(fos!=null){
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (canRecycle) {
                    bitmap.recycle();
                }
            }
        }
    }

    public static void saveBitmap(String path, Bitmap bitmap, int quality){
        saveBitmap(path, bitmap, quality, true);
    }
}
