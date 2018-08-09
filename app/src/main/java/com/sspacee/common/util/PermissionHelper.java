package com.sspacee.common.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.timeline.vpn.R;

import java.util.Arrays;
import java.util.List;


public class PermissionHelper {
    public static final int CODE_GET_ACCOUNTS = 0;
    public static final int CODE_READ_PHONE_STATE = 1;
    public static final int CODE_ACCESS_COARSE_LOCATION = 2;
    public static final int CODE_WRITE_EXTERNAL_STORAGE = 3;
//    public static final String GET_ACCOUNTS = Manifest.permission.GET_ACCOUNTS;
    public static final String READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE;
    public static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String REQUEST_INSTALL_PACKAGES = Manifest.permission.REQUEST_INSTALL_PACKAGES;
    public static List<String> requestPermissions = Arrays.asList(READ_PHONE_STATE,WRITE_EXTERNAL_STORAGE,ACCESS_COARSE_LOCATION);
//    static {
//        if(SystemUtils.isZH(MyApplication.getInstance())){
//
//        }else{
//            requestPermissions = Arrays.asList(WRITE_EXTERNAL_STORAGE,ACCESS_COARSE_LOCATION);
//        }
//    }
//    static {
//        LogUtil.i("os version="+Build.VERSION.SDK_INT );
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O){
//            requestPermissions.add(REQUEST_INSTALL_PACKAGES);
//        }
//    }
    public static final String PACKAGE = "package:";
    private Activity mContext;

    public PermissionHelper(Activity context) {
        this.mContext = context;
    }

    /**
     * 判断权限集合
     *
     * @return 权限已全部获取返回true，未全部获取返回false
     */
    public boolean checkPermissions() {
        for (String permission : requestPermissions) {
            if (!checkPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean checkNeedPermissions() {
        boolean ret = false;
        for (String perm:requestPermissions) {
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
        for (String perm:requestPermissions) {
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
     * 判断权限是否获取
     *
     * @param permission 权限名称
     * @return 已授权返回true，未授权返回false
     */
    public boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(mContext, permission) == PackageManager.PERMISSION_GRANTED;
    }


    /**
     * 获取权限
     *
     * @param resultCode
     * @return
     */
    public void permissionsCheck(String permission, int resultCode) {
        ActivityCompat.requestPermissions(mContext, new String[]{permission}, resultCode);
        // 注意这里要使用shouldShowRequestPermissionRationale而不要使用requestPermission方法
        if (ActivityCompat.shouldShowRequestPermissionRationale(mContext, permission)) {
            //如果用户以前拒绝过改权限申请，则给用户提示
            LogUtil.i("shouldShowRequestPermissionRationale"+permission);
            showMissingPermissionDialog();
        } else {
            ActivityCompat.requestPermissions(mContext, new String[]{permission}, resultCode);
        }

//        ActivityCompat.requestPermissions((Activity) mContext, new String[]{permission},resultCode);
    }


    // 显示缺失权限提示
    private void showMissingPermissionDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final AlertDialog alertDialog = builder.create();

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

    // 启动应用的设置
    public void startAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse(PACKAGE + mContext.getPackageName()));
        mContext.startActivityForResult(intent, 1);
    }
}