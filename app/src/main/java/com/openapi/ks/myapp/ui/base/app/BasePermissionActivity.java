package com.openapi.ks.myapp.ui.base.app;

import static com.luck.picture.lib.permissions.PermissionConfig.WRITE_EXTERNAL_STORAGE;
import static com.openapi.commons.common.util.PermissionHelper.CAMERA;
import static com.openapi.commons.common.util.PermissionHelper.READ_EXTERNAL_STORAGE;
import static com.openapi.commons.common.util.PermissionHelper.RECORD_AUDIO;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.openapi.commons.common.ui.base.LogActivity;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PermissionHelper;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.data.config.PermEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class BasePermissionActivity extends LogActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, EasyPermissions.PermissionCallbacks,
        EasyPermissions.RationaleCallbacks {

    public PermissionHelper mPermissionHelper;
    public static final int RC_CAMERA_PERM = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initPerm();
        initVosk();
    }

//----------------------------------------------------------
    public boolean hasPerm() {
        return EasyPermissions.hasPermissions(this, PermissionHelper.requestPermissions.toArray(new String[PermissionHelper.requestPermissions.size()]));
    }

    private void request() {
        LogUtil.i("perm 弹窗");
        EasyPermissions.requestPermissions(this,
                getString(R.string.permission_need_toast), RC_CAMERA_PERM,
                RECORD_AUDIO, CAMERA, WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void initPermEvent(PermEvent event){
        initPerm();
    }
    @AfterPermissionGranted(RC_CAMERA_PERM)
    public void initPerm(){
        LogUtil.i("onTouchEvent stop");
        if(!hasPerm()){
            request();
        }
    }
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
        LogUtil.i("onRequestPermissionsResult");
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    ToastUtil.showLong(R.string.permission_need_toast);
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
        if(hasPerm()){
            ToastUtil.showShort(R.string.permission_need_toast);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        initVosk();
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE:
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

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        LogUtil.i("onPermissionsGranted");
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this).setRationale(R.string.permission_need).build().show();
        }
    }

    @Override
    public void onRationaleAccepted(int requestCode) {
        LogUtil.i("onRationaleAccepted");
    }

    @Override
    public void onRationaleDenied(int requestCode) {
        LogUtil.i("onRationaleDenied");
    }


}
