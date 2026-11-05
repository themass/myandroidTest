package com.openapi.core.util.permission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.openapi.core.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 兼容各种Android版本的权限管理组件
 * 自动选择最适合的权限请求方式
 */
public class CompatiblePermissionManager {
    
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
    
    private final Activity activity;
    private final Map<Integer, PermissionCallback> requestCallbacks = new HashMap<>();
    private final Map<String, PermissionCallback> permissionCallbacks = new HashMap<>();
    private int requestCodeCounter = 1000;
    
    public CompatiblePermissionManager(Activity activity) {
        this.activity = activity;
        initPermissionSystem();
    }
    
    private void initPermissionSystem() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            LogUtil.i("使用传统权限系统 (Android 6.0+)");
        } else {
            LogUtil.i("使用传统权限系统 (Android < 6.0)");
        }
    }
    
    /**
     * 检查单个权限是否已授予
     */
    public boolean hasPermission(@NonNull String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6.0以下，权限在安装时授予
            return true;
        }
    }
    
    /**
     * 检查多个权限是否都已授予
     */
    public boolean hasPermissions(@NonNull String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查权限列表是否都已授予
     */
    public boolean hasPermissions(@NonNull List<String> permissions) {
        return hasPermissions(permissions.toArray(new String[0]));
    }
    
    /**
     * 请求权限（兼容所有版本）
     */
    public void requestPermissions(@NonNull String[] permissions, PermissionCallback callback) {
        requestPermissions(Arrays.asList(permissions), callback);
    }
    
    /**
     * 请求权限（兼容所有版本）
     */
    public void requestPermissions(@NonNull List<String> permissions, PermissionCallback callback) {
        // 过滤已授予的权限
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (permissionsToRequest.isEmpty()) {
            // 所有权限都已授予
            callback.onPermissionsGranted(permissions);
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 使用传统方式
            int requestCode = requestCodeCounter++;
            requestCallbacks.put(requestCode, callback);
            
            // 请求权限
            ActivityCompat.requestPermissions(activity, 
                    permissionsToRequest.toArray(new String[0]), requestCode);
        } else {
            // Android 6.0以下，权限在安装时授予，直接调用成功回调
            LogUtil.i("传统权限系统：权限已在安装时授予");
            callback.onPermissionsGranted(permissions);
        }
    }
    
    /**
     * 请求权限（带说明）
     */
    public void requestPermissionsWithRationale(@NonNull List<String> permissions, 
                                               @NonNull String rationale,
                                               PermissionCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查是否需要显示说明
            boolean shouldShowRationale = false;
            for (String permission : permissions) {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    shouldShowRationale = true;
                    break;
                }
            }
            
            if (shouldShowRationale) {
                // 显示说明对话框
                showRationaleDialog(permissions, rationale, callback);
            } else {
                // 直接请求权限
                requestPermissions(permissions, callback);
            }
        } else {
            // Android 6.0以下，直接调用成功回调
            callback.onPermissionsGranted(permissions);
        }
    }
    
    /**
     * 请求默认权限
     */
    public void requestDefaultPermissions(PermissionCallback callback) {
        requestPermissions(DEFAULT_PERMISSIONS, callback);
    }
    
    /**
     * 权限请求结果处理
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        PermissionCallback callback = requestCallbacks.get(requestCode);
        if (callback != null) {
            List<String> grantedPermissions = new ArrayList<>();
            List<String> deniedPermissions = new ArrayList<>();
            
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                
                if (granted) {
                    grantedPermissions.add(permission);
                    callback.onPermissionGranted(permission);
                } else {
                    deniedPermissions.add(permission);
                    callback.onPermissionDenied(permission);
                }
            }
            
            if (!grantedPermissions.isEmpty()) {
                callback.onPermissionsGranted(grantedPermissions);
            }
            
            if (!deniedPermissions.isEmpty()) {
                callback.onPermissionsDenied(deniedPermissions);
            }
            
            requestCallbacks.remove(requestCode);
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    private void showRationaleDialog(@NonNull List<String> permissions, 
                                   @NonNull String rationale,
                                   PermissionCallback callback) {
        // 这里可以显示自定义的说明对话框
        // 为了简化，直接请求权限
        requestPermissions(permissions, callback);
    }
    
    /**
     * 检查权限状态
     */
    private void checkPermissionsStatus() {
        LogUtil.i("检查权限状态");
    }
    
    /**
     * 打开应用设置页面
     */
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
    
    /**
     * 检查是否有权限被永久拒绝
     */
    public boolean hasPermanentlyDeniedPermissions(@NonNull List<String> permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (!hasPermission(permission) && 
                    !activity.shouldShowRequestPermissionRationale(permission)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 获取被拒绝的权限
     */
    public List<String> getDeniedPermissions(@NonNull List<String> permissions) {
        List<String> deniedPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                deniedPermissions.add(permission);
            }
        }
        return deniedPermissions;
    }
    
    /**
     * 检查是否需要显示权限说明
     */
    public boolean shouldShowRequestPermissionRationale(@NonNull String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.shouldShowRequestPermissionRationale(permission);
        }
        return false;
    }
    
    /**
     * 权限回调接口
     */
    public interface PermissionCallback {
        void onPermissionGranted(@NonNull String permission);
        void onPermissionDenied(@NonNull String permission);
        void onPermissionsGranted(@NonNull List<String> permissions);
        void onPermissionsDenied(@NonNull List<String> permissions);
    }
    
    /**
     * 简化的权限回调实现
     */
    public static abstract class SimplePermissionCallback implements PermissionCallback {
        @Override
        public void onPermissionGranted(@NonNull String permission) {
            // 默认实现为空
        }
        
        @Override
        public void onPermissionDenied(@NonNull String permission) {
            // 默认实现为空
        }
        
        @Override
        public void onPermissionsGranted(@NonNull List<String> permissions) {
            // 默认实现为空
        }
        
        @Override
        public void onPermissionsDenied(@NonNull List<String> permissions) {
            // 默认实现为空
        }
    }
}
