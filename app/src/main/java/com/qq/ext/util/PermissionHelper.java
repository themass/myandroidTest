package com.qq.ext.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.qq.Constants;
import com.qq.MyApplication;
import com.qq.network.R;
import com.qq.vpn.main.MainActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.weyye.hipermission.HiPermission;
import me.weyye.hipermission.PermissionCallback;
import me.weyye.hipermission.PermissionItem;


public class PermissionHelper {
//    public static final String GET_ACCOUNTS = Manifest.permission.GET_ACCOUNTS;
    public static final String READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE;
    public static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String REQUEST_INSTALL_PACKAGES = Manifest.permission.REQUEST_INSTALL_PACKAGES;
    public static List<String> requestPermissions = Arrays.asList(READ_PHONE_STATE,WRITE_EXTERNAL_STORAGE,ACCESS_COARSE_LOCATION);
    static {
        if(SystemUtils.isZH(MyApplication.getInstance())){

        }else{
            requestPermissions = Arrays.asList(WRITE_EXTERNAL_STORAGE,ACCESS_COARSE_LOCATION);
        }
    }
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
    public static boolean checkPermissions(Context context) {
        for (String permission : requestPermissions) {
            if (!checkPermission(context,permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean checkNeedPermissions(Context context) {
        boolean ret = false;
        for (String perm:requestPermissions) {
            if (!checkPermission(context,perm)) {
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
            if (!checkPermission(context,perm)) {
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
    public static boolean checkPermission(Context context,String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
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
            com.qq.ext.util.LogUtil.i("shouldShowRequestPermissionRationale"+permission);
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
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
                mContext.finish();
            }
        });
        builder.setPositiveButton(R.string.setting, new DialogInterface.OnClickListener() {
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

    public static void showPermit(Context context){
        List<PermissionItem> permissionItems = new ArrayList<PermissionItem>();
        permissionItems.add(new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getString(R.string.write_external_storage), R.drawable.permission_ic_storage));
        permissionItems.add(new PermissionItem(Manifest.permission.ACCESS_COARSE_LOCATION, context.getString(R.string.access_coarse_location), R.drawable.permission_ic_location));
        permissionItems.add(new PermissionItem(Manifest.permission.READ_PHONE_STATE, context.getString(R.string.read_phone_state), R.drawable.permission_ic_phone));

        HiPermission.create(context)
                .title(context.getString(R.string.permission_cus_title))
                .permissions(permissionItems)
                .msg(context.getString(R.string.permission_cus_msg))
                .animStyle(R.style.PermissionAnimScale)
                .style(R.style.PermissionDefaultBlueStyle)
                .checkMutiPermission(new PermissionCallback() {
                    @Override
                    public void onClose() {
                        LogUtil.i("onClose");
                    }

                    @Override
                    public void onFinish() {
                        LogUtil.i("onFinish");
                    }

                    @Override
                    public void onDeny(String permission, int position) {
                        LogUtil.i("onDeny:"+permission);
                    }

                    @Override
                    public void onGuarantee(String permission, int position) {
                        LogUtil.i("onGuarantee:"+permission);
                    }
                });
    }


}