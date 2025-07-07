package com.openapi.ks.myapp.ui.base.app;

import static com.openai.commonutil.PermissionHelper.CAMERA;
import static com.openai.commonutil.PermissionHelper.READ_EXTERNAL_STORAGE;
import static com.openai.commonutil.PermissionHelper.RECORD_AUDIO;
import static com.openai.commonutil.PermissionHelper.WRITE_EXTERNAL_STORAGE;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.openai.commonutil.LogUtil;
import com.openai.commonutil.PermissionHelper;
import com.openai.commonutil.ToastUtil;
import com.openai.commonevent.PermEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
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
    public boolean hasPerm(List<String> perms) {
        return EasyPermissions.hasPermissions(this, PermissionHelper.requestPermissions.toArray(new String[PermissionHelper.requestPermissions.size()]));
    }

    private void request(String... perms) {
        LogUtil.i("perm 弹窗");
        EasyPermissions.requestPermissions(this,
                getString(com.openai.commonutil.R.string.permission_need_toast), RC_CAMERA_PERM, perms);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void initPermEvent(PermEvent event){
        if(!hasPerm(event.perms)){
            request(event.perms.toArray(new String[event.perms.size()]));
        }
    }
    @AfterPermissionGranted(RC_CAMERA_PERM)
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
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
        LogUtil.i("onRequestPermissionsResult");
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    ToastUtil.showLong(com.openai.commonutil.R.string.permission_need_toast);
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
//            ToastUtil.showShort(com.openai.commonutil.R.string.permission_need_toast);
//        }
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
            new AppSettingsDialog.Builder(this).setRationale(com.openai.commonutil.R.string.permission_need).build().show();
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
