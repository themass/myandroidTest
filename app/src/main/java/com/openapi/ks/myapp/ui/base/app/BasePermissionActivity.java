package com.openapi.ks.myapp.ui.base.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.openapi.core.util.LogUtil;
import com.openapi.core.util.PermissionHelper;
import com.openapi.core.util.ToastUtil;
import com.openapi.core.util.permission.CompatiblePermissionManager;
import com.openapi.core.util.permission.PermissionUtils;
import com.openapi.core.event.PermEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.openapi.core.util.permission.ModernPermissionManager;

public class BasePermissionActivity extends LogActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    public PermissionHelper mPermissionHelper;
    public CompatiblePermissionManager mCompatiblePermissionManager;
    public ModernPermissionManager mModernPermissionManager;
    public static final int RC_CAMERA_PERM = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initPermissionManager();
        initPerm();
        initVosk();
    }

    /**
     * 初始化权限管理器
     */
    private void initPermissionManager() {
        mCompatiblePermissionManager = new CompatiblePermissionManager(this);
        mModernPermissionManager = new ModernPermissionManager(this);
    }

//----------------------------------------------------------
    /**
     * 检查权限（兼容所有版本）
     */
    public boolean hasPerm(List<String> perms) {
        if (mModernPermissionManager != null) {
            return mModernPermissionManager.hasPermissions(perms);
        }
        if (mCompatiblePermissionManager != null) {
            return mCompatiblePermissionManager.hasPermissions(perms);
        }
        return false;
    }

    /**
     * 请求权限（兼容所有版本）
     */
    private void request(String... perms) {
        LogUtil.i("perm 弹窗");
        if (mModernPermissionManager != null) {
            mModernPermissionManager.requestPermissions(perms, new ModernPermissionManager.PermissionCallback() {
                @Override
                public void onPermissionsGranted(@NonNull List<String> permissions) {
                    LogUtil.i("权限已授予: " + permissions);
                    initVosk();
                }

                @Override
                public void onPermissionsDenied(@NonNull List<String> permissions) {
                    LogUtil.i("权限被拒绝: " + permissions);
                    ToastUtil.showLong(com.openapi.core.util.R.string.permission_need_toast);
                }

                @Override
                public void onPermissionRationaleNeeded(@NonNull List<String> permissions) {
                    LogUtil.i("需要权限说明: " + permissions);
                    // 可以在这里显示权限说明对话框
                }
            });
        } else if (mCompatiblePermissionManager != null) {
            mCompatiblePermissionManager.requestPermissions(Arrays.asList(perms), new CompatiblePermissionManager.SimplePermissionCallback() {
                @Override
                public void onPermissionsGranted(@NonNull List<String> permissions) {
                    LogUtil.i("权限已授予: " + permissions);
                    initVosk();
                }

                @Override
                public void onPermissionsDenied(@NonNull List<String> permissions) {
                    LogUtil.i("权限被拒绝: " + permissions);
                    ToastUtil.showLong(com.openapi.core.util.R.string.permission_need_toast);
                }
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void initPermEvent(PermEvent event){
        if(!hasPerm(event.perms)){
            request(event.perms.toArray(new String[event.perms.size()]));
        }
    }
    
    public void initPerm(){
        LogUtil.i("onTouchEvent stop");
        if(!hasPerm(new ArrayList<>())){
            request();
        }
    }
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // 使用新的权限管理器处理结果
        if (mCompatiblePermissionManager != null) {
            mCompatiblePermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        
        LogUtil.i("onRequestPermissionsResult");
        
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    ToastUtil.showLong(com.openapi.core.util.R.string.permission_need_toast);
                    return;
                }
            }
           boolean ret = Arrays.stream(grantResults).allMatch(o -> o == PackageManager.PERMISSION_GRANTED);
            if(ret) {
                initVosk();
            }
        }
    }
    public void fromSetting() {
//        if(hasPerm()){
//            ToastUtil.showShort(com.openapi.core.util.R.string.permission_need_toast);
//        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        initVosk();
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 1001: // AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE
                    fromSetting();
                    break;
            }
        }
    }
    //----------------------------------------------------------------------
    public void initVosk() {
        if(mPermissionHelper == null) {
            mPermissionHelper = new PermissionHelper(this);
        }
    }

    // 权限管理相关方法 - 使用现代权限管理器
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        LogUtil.i("onPermissionsGranted");
    }

    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        // 使用现代权限管理器检查权限是否被永久拒绝
        if (mModernPermissionManager != null) {
            // 检查是否有权限被永久拒绝
            boolean hasPermanentlyDenied = false;
            for (String perm : perms) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!shouldShowRequestPermissionRationale(perm)) {
                        hasPermanentlyDenied = true;
                        break;
                    }
                }
            }
            
            if (hasPermanentlyDenied) {
                // 显示设置对话框
                new AlertDialog.Builder(this)
                        .setTitle("需要权限")
                        .setMessage("应用需要相关权限才能正常工作，请在设置中开启权限")
                        .setPositiveButton("设置", (dialog, which) -> {
                            mModernPermissionManager.openAppSettings();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }
    }

    public void onRationaleAccepted(int requestCode) {
        LogUtil.i("onRationaleAccepted");
    }

    public void onRationaleDenied(int requestCode) {
        LogUtil.i("onRationaleDenied");
    }


}
