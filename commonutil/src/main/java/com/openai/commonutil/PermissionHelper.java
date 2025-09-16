package com.openai.commonutil;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.openai.commonutil.permission.CompatiblePermissionManager;
import com.openai.commonutil.permission.PermissionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 权限助手类（兼容所有版本）
 * 提供向后兼容的权限管理API
 */
public class PermissionHelper {
    public static final int CODE_GET_ACCOUNTS = 0;
    private static final int REQUEST_CODE_SETTINGS = 1001;
    public static final int CODE_READ_PHONE_STATE = 1;
    public static final int CODE_ACCESS_COARSE_LOCATION = 2;
    public static final int CODE_WRITE_EXTERNAL_STORAGE = 3;
    //    public static final String GET_ACCOUNTS = Manifest.permission.GET_ACCOUNTS;
    public static final String READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE;
    public static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;

    public static final String CAMERA = Manifest.permission.CAMERA;
    public static final String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    public static List<String> requestPermissions = Arrays.asList(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE);

    //    static {
//        LogUtil.i("os version="+Build.VERSION.SDK_INT );
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O){
//            requestPermissions.add(REQUEST_INSTALL_PACKAGES);
//        }
//    }
    public static final String PACKAGE = "package:";
    private Activity mContext;
    private CompatiblePermissionManager mPermissionManager;

    public PermissionHelper(Activity context) {
        this.mContext = context;
        this.mPermissionManager = new CompatiblePermissionManager(context);
    }

    /**
     * 判断权限集合（兼容所有版本）
     *
     * @return 权限已全部获取返回true，未全部获取返回false
     */
    public boolean checkPermissions(List<String> permissions) {
        if (mPermissionManager != null) {
            return mPermissionManager.hasPermissions(permissions);
        }
        // 降级到传统方式
        for (String permission : permissions) {
            if (!checkPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean checkNeedPermissions() {
        boolean ret = false;
        for (String perm : requestPermissions) {
            if (!checkPermission(perm)) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(mContext, perm)) {
                    ActivityCompat.requestPermissions(mContext, new String[]{perm}, 1);
                    ret = true;
                    break;
                }
            }
        }
        if (ret) {
            return ret;
        }
        for (String perm : requestPermissions) {
            if (!checkPermission(perm)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(mContext, perm)) {
                    //如果用户以前拒绝过改权限申请，则给用户提示
                    showMissingPermissionDialog();
                    break;
                }
            }
        }
        return true;
    }

    /**
     * 判断权限是否获取（兼容所有版本）
     *
     * @param permission 权限名称
     * @return 已授权返回true，未授权返回false
     */
    public boolean checkPermission(String permission) {
        if (mPermissionManager != null) {
            return mPermissionManager.hasPermission(permission);
        }
        // 降级到传统方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(mContext, permission) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6.0以下，权限在安装时授予
            return true;
        }
    }


    /**
     * 获取权限（兼容所有版本）
     *
     * @param permission 权限名称
     * @param resultCode 结果代码
     */
    public void permissionsCheck(String permission, int resultCode) {
        if (mPermissionManager != null) {
            mPermissionManager.requestPermissions(Arrays.asList(permission), new CompatiblePermissionManager.SimplePermissionCallback() {
                @Override
                public void onPermissionsGranted(@NonNull List<String> permissions) {
                    LogUtil.i("权限已授予: " + permissions);
                }

                @Override
                public void onPermissionsDenied(@NonNull List<String> permissions) {
                    LogUtil.i("权限被拒绝: " + permissions);
                    showMissingPermissionDialog();
                }
            });
        } else {
            // 降级到传统方式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(mContext, permission)) {
                    //如果用户以前拒绝过改权限申请，则给用户提示
                    showMissingPermissionDialog();
                } else {
                    ActivityCompat.requestPermissions(mContext, new String[]{permission}, resultCode);
                }
            } else {
                // Android 6.0以下，权限在安装时授予
                LogUtil.i("传统权限系统：权限已在安装时授予");
            }
        }
    }


    // 显示缺失权限提示
    private void showMissingPermissionDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final AlertDialog alertDialog = builder.create();
        ToastUtil.showLong(R.string.permission_need_toast);
        builder.setMessage(R.string.permission_need);
        // 拒绝, 退出应用
        builder.setNegativeButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
                mContext.finish();
            }
        });
        builder.setPositiveButton(R.string.menu_btn_setting, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startAppSettings();
            }
        });
        builder.show();
    }

    // 启动应用的设置（兼容所有版本）
    public void startAppSettings() {
        if (mPermissionManager != null) {
            mPermissionManager.openAppSettings();
        } else {
            // 降级到传统方式
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse(PACKAGE + mContext.getPackageName()));
            // 保持原有逻辑，使用startActivityForResult检查权限状态变化
            if (mContext instanceof Activity) {
                ((Activity) mContext).startActivityForResult(intent, REQUEST_CODE_SETTINGS);
            } else {
                mContext.startActivity(intent);
            }
        }
    }
    
    /**
     * 获取权限管理器
     */
    public CompatiblePermissionManager getPermissionManager() {
        return mPermissionManager;
    }
    
    /**
     * 处理权限请求结果（用于传统方式）
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mPermissionManager != null) {
            mPermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}