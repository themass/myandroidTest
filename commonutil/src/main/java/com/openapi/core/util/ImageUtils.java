package com.openapi.core.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class ImageUtils {
    public static byte[] getImageBytes(Context context, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            LogUtil.e(e);
            return null;
        }
    }
    public static MultipartBody.Part getImageMultipart(Context context, String filePath){
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        RequestBody requestFile = RequestBody.create(file, okhttp3.MediaType.get("image/*"));
        return MultipartBody.Part.createFormData("image", file.getName(), requestFile);
    }
}
