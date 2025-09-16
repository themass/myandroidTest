package com.openai.commonutil.permission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 现代权限管理器 - 使用官方Activity Result API替代EasyPermissions
 * 支持Android 6.0+的权限管理
 */
public class ModernPermissionManager {
    
    private static final String TAG = "ModernPermissionManager";
    
    private final Activity activity;
    private final Context context;
    private PermissionCallback permissionCallback;
    
    // 单个权限请求
    private ActivityResultLauncher<String> singlePermissionLauncher;
    
    // 多个权限请求
    private ActivityResultLauncher<String[]> multiplePermissionsLauncher;
    
    // 设置页面请求
    private ActivityResultLauncher<Intent> settingsLauncher;
    
    public interface PermissionCallback {
        void onPermissionsGranted(@NonNull List<String> permissions);
        void onPermissionsDenied(@NonNull List<String> permissions);
        void onPermissionRationaleNeeded(@NonNull List<String> permissions);
    }
    
    public ModernPermissionManager(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        initLaunchers();
    }
    
    private void initLaunchers() {
        // 检查Activity是否支持registerForActivityResult
        if (activity instanceof androidx.fragment.app.FragmentActivity) {
            androidx.fragment.app.FragmentActivity fragmentActivity = (androidx.fragment.app.FragmentActivity) activity;
            
            // 单个权限请求
            singlePermissionLauncher = fragmentActivity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        LogUtil.i(TAG, "单个权限已授予");
                        if (permissionCallback != null) {
                            permissionCallback.onPermissionsGranted(new ArrayList<>());
                        }
                    } else {
                        LogUtil.i(TAG, "单个权限被拒绝");
                        if (permissionCallback != null) {
                            permissionCallback.onPermissionsDenied(new ArrayList<>());
                        }
                    }
                }
            );
            
            // 多个权限请求
            multiplePermissionsLauncher = fragmentActivity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    List<String> grantedPermissions = new ArrayList<>();
                    List<String> deniedPermissions = new ArrayList<>();
                    
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (entry.getValue()) {
                            grantedPermissions.add(entry.getKey());
                        } else {
                            deniedPermissions.add(entry.getKey());
                        }
                    }
                    
                    LogUtil.i(TAG, "权限请求结果 - 已授予: " + grantedPermissions + ", 被拒绝: " + deniedPermissions);
                    
                    if (permissionCallback != null) {
                        if (!grantedPermissions.isEmpty()) {
                            permissionCallback.onPermissionsGranted(grantedPermissions);
                        }
                        if (!deniedPermissions.isEmpty()) {
                            permissionCallback.onPermissionsDenied(deniedPermissions);
                        }
                    }
                }
            );
            
            // 设置页面请求
            settingsLauncher = fragmentActivity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    LogUtil.i(TAG, "从设置页面返回");
                    // 重新检查权限
                    checkPermissions();
                }
            );
        } else {
            LogUtil.w(TAG, "Activity不支持registerForActivityResult，权限管理功能可能不可用");
        }
    }
    
    /**
     * 检查是否有指定权限
     */
    public boolean hasPermissions(@NonNull List<String> permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查是否有指定权限
     */
    public boolean hasPermissions(@NonNull String... permissions) {
        return hasPermissions(Arrays.asList(permissions));
    }
    
    /**
     * 请求单个权限
     */
    public void requestPermission(@NonNull String permission, @NonNull PermissionCallback callback) {
        this.permissionCallback = callback;
        
        if (hasPermissions(permission)) {
            LogUtil.i(TAG, "权限已存在: " + permission);
            callback.onPermissionsGranted(Arrays.asList(permission));
            return;
        }
        
        if (shouldShowRationale(permission)) {
            LogUtil.i(TAG, "需要显示权限说明: " + permission);
            callback.onPermissionRationaleNeeded(Arrays.asList(permission));
        }
        
        LogUtil.i(TAG, "请求权限: " + permission);
        singlePermissionLauncher.launch(permission);
    }
    
    /**
     * 请求多个权限
     */
    public void requestPermissions(@NonNull List<String> permissions, @NonNull PermissionCallback callback) {
        this.permissionCallback = callback;
        
        // 过滤出未授予的权限
        List<String> deniedPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermissions(permission)) {
                deniedPermissions.add(permission);
            }
        }
        
        if (deniedPermissions.isEmpty()) {
            LogUtil.i(TAG, "所有权限已存在");
            callback.onPermissionsGranted(permissions);
            return;
        }
        
        LogUtil.i(TAG, "请求权限: " + deniedPermissions);
        multiplePermissionsLauncher.launch(deniedPermissions.toArray(new String[0]));
    }
    
    /**
     * 请求多个权限
     */
    public void requestPermissions(@NonNull String[] permissions, @NonNull PermissionCallback callback) {
        requestPermissions(Arrays.asList(permissions), callback);
    }
    
    /**
     * 检查权限并处理结果
     */
    public void checkPermissions() {
        if (permissionCallback != null) {
            // 这里可以根据需要实现权限检查逻辑
            LogUtil.i(TAG, "检查权限状态");
        }
    }
    
    /**
     * 打开应用设置页面
     */
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        settingsLauncher.launch(intent);
    }
    
    /**
     * 显示权限被拒绝的提示
     */
    public void showPermissionDeniedMessage() {
        ToastUtil.showLong(com.openai.commonutil.R.string.permission_need_toast);
    }
    
    /**
     * 检查是否应该显示权限说明
     */
    private boolean shouldShowRationale(@NonNull String permission) {
        return activity.shouldShowRequestPermissionRationale(permission);
    }
    
    /**
     * 检查是否应该显示权限说明
     */
    private boolean shouldShowRationale(@NonNull List<String> permissions) {
        for (String permission : permissions) {
            if (shouldShowRationale(permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取常用权限列表
     */
    public static class CommonPermissions {
        public static final String[] CAMERA = {Manifest.permission.CAMERA};
        public static final String[] STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        public static final String[] AUDIO = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        };
        public static final String[] LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        public static final String[] ALL = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
    }
}
