package com.openai.commonutil.permission;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.openai.commonutil.LogUtil;

import java.util.Arrays;
import java.util.List;

/**
 * 权限管理示例Activity
 * 展示如何使用新的权限管理组件
 */
public class PermissionExampleActivity extends Activity {
    
    private CompatiblePermissionManager mPermissionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化权限管理器
        mPermissionManager = new CompatiblePermissionManager(this);
        
        // 示例：请求默认权限
        requestDefaultPermissions();
    }
    
    /**
     * 请求默认权限
     */
    private void requestDefaultPermissions() {
        mPermissionManager.requestDefaultPermissions(new CompatiblePermissionManager.SimplePermissionCallback() {
            @Override
            public void onPermissionsGranted(@NonNull List<String> permissions) {
                LogUtil.i("默认权限已授予: " + permissions);
                Toast.makeText(PermissionExampleActivity.this, "权限已授予", Toast.LENGTH_SHORT).show();
                
                // 权限授予后可以执行相关操作
                performOperationsAfterPermissionGranted();
            }
            
            @Override
            public void onPermissionsDenied(@NonNull List<String> permissions) {
                LogUtil.i("默认权限被拒绝: " + permissions);
                Toast.makeText(PermissionExampleActivity.this, "权限被拒绝", Toast.LENGTH_SHORT).show();
                
                // 权限被拒绝后的处理
                handlePermissionDenied(permissions);
            }
        });
    }
    
    /**
     * 请求特定权限
     */
    private void requestSpecificPermissions() {
        List<String> permissions = Arrays.asList(
                CompatiblePermissionManager.CAMERA,
                CompatiblePermissionManager.RECORD_AUDIO
        );
        
        mPermissionManager.requestPermissions(permissions, new CompatiblePermissionManager.SimplePermissionCallback() {
            @Override
            public void onPermissionsGranted(@NonNull List<String> permissions) {
                LogUtil.i("特定权限已授予: " + permissions);
                // 执行需要这些权限的操作
            }
            
            @Override
            public void onPermissionsDenied(@NonNull List<String> permissions) {
                LogUtil.i("特定权限被拒绝: " + permissions);
                // 处理权限被拒绝的情况
            }
        });
    }
    
    /**
     * 请求权限（带说明）
     */
    private void requestPermissionsWithRationale() {
        List<String> permissions = Arrays.asList(CompatiblePermissionManager.CAMERA);
        String rationale = "需要相机权限来拍照";
        
        mPermissionManager.requestPermissionsWithRationale(permissions, rationale, 
                new CompatiblePermissionManager.SimplePermissionCallback() {
            @Override
            public void onPermissionsGranted(@NonNull List<String> permissions) {
                LogUtil.i("带说明的权限已授予: " + permissions);
            }
            
            @Override
            public void onPermissionsDenied(@NonNull List<String> permissions) {
                LogUtil.i("带说明的权限被拒绝: " + permissions);
            }
        });
    }
    
    /**
     * 检查权限状态
     */
    private void checkPermissionStatus() {
        // 检查单个权限
        boolean hasCamera = mPermissionManager.hasPermission(CompatiblePermissionManager.CAMERA);
        LogUtil.i("相机权限状态: " + hasCamera);
        
        // 检查多个权限
        List<String> permissions = Arrays.asList(
                CompatiblePermissionManager.CAMERA,
                CompatiblePermissionManager.RECORD_AUDIO
        );
        boolean hasAllPermissions = mPermissionManager.hasPermissions(permissions);
        LogUtil.i("所有权限状态: " + hasAllPermissions);
        
        // 获取被拒绝的权限
        List<String> deniedPermissions = mPermissionManager.getDeniedPermissions(permissions);
        LogUtil.i("被拒绝的权限: " + deniedPermissions);
    }
    
    /**
     * 权限授予后执行的操作
     */
    private void performOperationsAfterPermissionGranted() {
        // 这里可以执行需要权限的操作
        // 例如：启动相机、录音等
        LogUtil.i("执行需要权限的操作");
    }
    
    /**
     * 处理权限被拒绝的情况
     */
    private void handlePermissionDenied(List<String> deniedPermissions) {
        // 检查是否有权限被永久拒绝
        if (mPermissionManager.hasPermanentlyDeniedPermissions(deniedPermissions)) {
            // 引导用户到设置页面
            mPermissionManager.openAppSettings();
        } else {
            // 可以再次请求权限或显示说明
            LogUtil.i("权限被拒绝，可以再次请求");
        }
    }
    
    /**
     * 使用PermissionUtils的简化API
     */
    private void usePermissionUtils() {
        // 检查权限
        boolean hasCamera = PermissionUtils.hasCameraPermission(this);
        boolean hasStorage = PermissionUtils.hasStoragePermissions(this);
        
        // 检查并执行操作
        PermissionUtils.checkAndExecute(this, 
                CompatiblePermissionManager.CAMERA,
                () -> {
                    // 权限已授予，执行操作
                    LogUtil.i("相机权限已授予，可以拍照");
                },
                () -> {
                    // 权限未授予，处理拒绝情况
                    LogUtil.i("相机权限未授予");
                }
        );
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 从设置页面返回后，重新检查权限
        if (resultCode == RESULT_OK) {
            checkPermissionStatus();
        }
    }
}
