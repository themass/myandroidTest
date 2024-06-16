package chat.ui;

import static com.luck.picture.lib.permissions.PermissionConfig.WRITE_EXTERNAL_STORAGE;
import static com.openapi.commons.common.util.PermissionHelper.CAMERA;
import static com.openapi.commons.common.util.PermissionHelper.RECORD_AUDIO;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.hrl.chaui.widget.RecordButton;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.entity.LocalMedia;
import com.openapi.commons.common.asr.AsrByteDance;
import com.openapi.commons.common.ui.view.InputRecordView;
import com.openapi.commons.common.ui.view.Mp3ToWav;
import com.openapi.commons.common.util.DoubleClickExit;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PermissionHelper;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.chatfree.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ConfigActionJump;
import com.openapi.ks.myapp.data.config.PermEvent;
import com.openapi.ks.myapp.service.LogUploadService;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.myapp.ui.user.LoginActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import chat.ui.adapter.MyMessagesListAdapter;
import chat.ui.data.fixtures.MessagesFixtures;
import chat.ui.data.model.ChatHistory;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.SimpleMessage;
import chat.ui.utils.AppUtils;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;
import tz.co.hosannahighertech.messagekit.messages.MessagesList;

public class CustomChatMessagesWithPermActivity extends CustomChatMessagesActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, EasyPermissions.PermissionCallbacks,
        EasyPermissions.RationaleCallbacks {

    public PermissionHelper mPermissionHelper;
    public static final int RC_CAMERA_PERM = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initPerm();
    }

    @Override
    public void setupView() {
        super.setupView();
        initVosk();
    }
//----------------------------------------------------------
private boolean hasPerm() {
    return EasyPermissions.hasPermissions(this, PermissionHelper.requestPermissions.toArray(new String[PermissionHelper.requestPermissions.size()]));
}

    private void request() {
        LogUtil.i("perm 弹窗");
        EasyPermissions.requestPermissions(this,
                getString(R.string.permission_need_toast), RC_CAMERA_PERM,
                RECORD_AUDIO, CAMERA, WRITE_EXTERNAL_STORAGE);
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

    @Override
    public void fromSetting() {
        super.fromSetting();
        if(hasPerm()){
            ToastUtil.showShort(R.string.permission_need_toast);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        initVosk();
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
