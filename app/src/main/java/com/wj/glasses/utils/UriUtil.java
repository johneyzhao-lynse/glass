package com.wj.glasses.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;

public class UriUtil {
    public static Uri fromFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            return FileProvider.getUriForFile(context, context.getPackageName()+".fileProvider", file);
        else
            return Uri.fromFile(file);
    }
}
