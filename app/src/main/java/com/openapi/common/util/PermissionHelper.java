package com.openapi.common.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.openapi.ks.free1.R;

import java.util.ArrayList;
import java.util.List;

import me.weyye.hipermission.HiPermission;
import me.weyye.hipermission.PermissionCallback;
import me.weyye.hipermission.PermissionItem;


public class PermissionHelper {
    //    public static final String GET_ACCOUNTS = Manifest.permission.GET_ACCOUNTS;
    public static final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    /**
     * 需要运行时申请的权限。
     * <p>
     * 这里原来是 {@code WRITE_EXTERNAL_STORAGE}。现在已有两处不再成立：
     * 一是文件写到了应用专属目录（{@link FileUtils#getWriteFilePath}），不需要任何存储权限；
     * 二是清单里该权限声明了 {@code maxSdkVersion=28}，Android 10+ 上系统直接说"没申请过"，
     * 于是 {@code checkPermissions} 永远返回 false、启动时不管用户点什么都卡在权限引导页
     * （实测：点"确定"跳到系统设置，而设置里根本没有可授予的存储权限）。
     * <p>
     * 保持空列表，让引导流程自然跳过；将来要加通知之类的运行时权限再加到这里。
     */
    public static List<String> requestPermissions = new ArrayList<>();
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
        // 启动时不再需要任何存储权限（见 requestPermissions 的说明），列表为空就不弹引导页
        if (requestPermissions.isEmpty()) {
            return;
        }
        List<PermissionItem> permissionItems = new ArrayList<PermissionItem>();
        permissionItems.add(new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getString(R.string.write_external_storage), me.weyye.hipermission.R.drawable.permission_ic_storage));

        HiPermission.create(context)
                .title(context.getString(R.string.permission_cus_title))
                .permissions(permissionItems)
                .msg(context.getString(R.string.permission_cus_msg))
                .animStyle(me.weyye.hipermission.R.style.PermissionAnimScale)
                .style(me.weyye.hipermission.R.style.PermissionDefaultBlueStyle)
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