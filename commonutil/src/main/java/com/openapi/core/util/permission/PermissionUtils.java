package com.openapi.core.util.permission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.openapi.core.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 权限工具类
 * 提供简化的权限管理API
 */
public class PermissionUtils {
    
    // 权限常量
    public static final String CAMERA = Manifest.permission.CAMERA;
    public static final String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    public static final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    public static final String READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE;
    public static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final String ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    
    // 权限组
    public static final String[] STORAGE_PERMISSIONS = {
            WRITE_EXTERNAL_STORAGE,
            READ_EXTERNAL_STORAGE
    };
    
    public static final String[] LOCATION_PERMISSIONS = {
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION
    };
    
    public static final String[] MEDIA_PERMISSIONS = {
            CAMERA,
            RECORD_AUDIO,
            WRITE_EXTERNAL_STORAGE,
            READ_EXTERNAL_STORAGE
    };
    
    // 默认权限列表
    public static final List<String> DEFAULT_PERMISSIONS = Arrays.asList(
            RECORD_AUDIO,
            WRITE_EXTERNAL_STORAGE,
            READ_EXTERNAL_STORAGE
    );
    
    /**
     * 检查单个权限是否已授予
     */
    public static boolean hasPermission(@NonNull Context context, @NonNull String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6.0以下，权限在安装时授予
            return true;
        }
    }
    
    /**
     * 检查多个权限是否都已授予
     */
    public static boolean hasPermissions(@NonNull Context context, @NonNull String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(context, permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查权限列表是否都已授予
     */
    public static boolean hasPermissions(@NonNull Context context, @NonNull List<String> permissions) {
        return hasPermissions(context, permissions.toArray(new String[0]));
    }
    
    /**
     * 获取被拒绝的权限
     */
    public static List<String> getDeniedPermissions(@NonNull Context context, @NonNull List<String> permissions) {
        List<String> deniedPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(context, permission)) {
                deniedPermissions.add(permission);
            }
        }
        return deniedPermissions;
    }
    
    /**
     * 检查是否有权限被永久拒绝
     */
    public static boolean hasPermanentlyDeniedPermissions(@NonNull Activity activity, @NonNull List<String> permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (!hasPermission(activity, permission) && 
                    !activity.shouldShowRequestPermissionRationale(permission)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查是否需要显示权限说明
     */
    public static boolean shouldShowRequestPermissionRationale(@NonNull Activity activity, @NonNull String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.shouldShowRequestPermissionRationale(permission);
        }
        return false;
    }
    
    /**
     * 打开应用设置页面
     */
    public static void openAppSettings(@NonNull Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
    
    /**
     * 检查权限并执行操作
     */
    public static void checkAndExecute(@NonNull Activity activity, 
                                     @NonNull List<String> permissions,
                                     @NonNull Runnable onGranted,
                                     @Nullable Runnable onDenied) {
        if (hasPermissions(activity, permissions)) {
            onGranted.run();
        } else {
            if (onDenied != null) {
                onDenied.run();
            }
        }
    }
    
    /**
     * 检查权限并执行操作（单个权限）
     */
    public static void checkAndExecute(@NonNull Activity activity, 
                                     @NonNull String permission,
                                     @NonNull Runnable onGranted,
                                     @Nullable Runnable onDenied) {
        checkAndExecute(activity, Arrays.asList(permission), onGranted, onDenied);
    }
    
    /**
     * 检查默认权限
     */
    public static boolean hasDefaultPermissions(@NonNull Context context) {
        return hasPermissions(context, DEFAULT_PERMISSIONS);
    }
    
    /**
     * 检查存储权限
     */
    public static boolean hasStoragePermissions(@NonNull Context context) {
        return hasPermissions(context, STORAGE_PERMISSIONS);
    }
    
    /**
     * 检查位置权限
     */
    public static boolean hasLocationPermissions(@NonNull Context context) {
        return hasPermissions(context, LOCATION_PERMISSIONS);
    }
    
    /**
     * 检查媒体权限
     */
    public static boolean hasMediaPermissions(@NonNull Context context) {
        return hasPermissions(context, MEDIA_PERMISSIONS);
    }
    
    /**
     * 检查相机权限
     */
    public static boolean hasCameraPermission(@NonNull Context context) {
        return hasPermission(context, CAMERA);
    }
    
    /**
     * 检查录音权限
     */
    public static boolean hasRecordAudioPermission(@NonNull Context context) {
        return hasPermission(context, RECORD_AUDIO);
    }
    
    /**
     * 获取权限状态描述
     */
    public static String getPermissionStatus(@NonNull Context context, @NonNull String permission) {
        if (hasPermission(context, permission)) {
            return "已授予";
        } else {
            return "未授予";
        }
    }
    
    /**
     * 获取权限组状态描述
     */
    public static String getPermissionsStatus(@NonNull Context context, @NonNull List<String> permissions) {
        List<String> deniedPermissions = getDeniedPermissions(context, permissions);
        if (deniedPermissions.isEmpty()) {
            return "所有权限已授予";
        } else {
            return "缺少权限: " + String.join(", ", deniedPermissions);
        }
    }
    
    /**
     * 权限状态枚举
     */
    public enum PermissionStatus {
        GRANTED,        // 已授予
        DENIED,         // 已拒绝
        PERMANENTLY_DENIED  // 永久拒绝
    }
    
    /**
     * 获取权限状态
     */
    public static PermissionStatus getPermissionStatus(@NonNull Activity activity, @NonNull String permission) {
        if (hasPermission(activity, permission)) {
            return PermissionStatus.GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.shouldShowRequestPermissionRationale(permission)) {
                return PermissionStatus.DENIED;
            } else {
                return PermissionStatus.PERMANENTLY_DENIED;
            }
        } else {
            return PermissionStatus.DENIED;
        }
    }
}
