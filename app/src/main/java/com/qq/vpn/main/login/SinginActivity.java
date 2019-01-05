package com.qq.vpn.main.login;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.qq.Constants;
import com.qq.ext.network.req.CommonResponse;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.StringUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.fq3.R;
import com.qq.vpn.domain.req.LoginForm;
import com.qq.vpn.domain.res.UserInfoVo;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.support.UserLoginUtil;
import com.qq.vpn.ui.base.actvity.BaseSingleActivity;

import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by dengt on 2016/8/13.
 */
public class SinginActivity extends BaseSingleActivity {
    private static final String TAG = "login_tag";
    @BindView(R.id.ll_loading)
    LinearLayout loading;
    @BindView(R.id.et_name)
    EditText etUserName;
    @BindView(R.id.et_pass)
    EditText etPassword;
    @BindView(R.id.btn_singin)
    Button btnLogin;
    @BindView(R.id.btn_singup)
    Button btnReg;
    NetApiUtil api;
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<UserInfoVo>() {
        @Override
        public void onResponse(UserInfoVo vo) {
            UserLoginUtil.login(SinginActivity.this, vo);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_singin);
        setToolbarTitle(R.string.singin, true);
        api = new NetApiUtil(this);
        String name = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_LAST, null);
        if (StringUtils.hasText(name)) {
            etUserName.setText(name);
        }
        String pwd = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_PW_LAST, null);
        if (StringUtils.hasText(pwd)) {
            etPassword.setText(pwd);
        }

    }

    @OnClick(R.id.btn_singup)
    public void singup(View view) {
        finish();
        startActivity(SingupActivity.class);
    }
    @OnClick(R.id.btn_singin)
    public void signin(View view) {
        String name = etUserName.getText().toString();
        String pwd = etPassword.getText().toString();
        if (!StringUtils.hasText(name) || !StringUtils.hasText(pwd)) {
            ToastUtil.showShort(R.string.empty_name_pwd);
            return;
        }
        if (!Constants.namePattern.matcher(name).matches()) {
            ToastUtil.showShort(R.string.pattern_name_err);
            return;
        }
        setEnabled(false);
        PreferenceUtils.setPrefString(this, Constants.LOGIN_USER_LAST, name);
        PreferenceUtils.setPrefString(this, Constants.LOGIN_USER_PW_LAST, pwd);
        LoginForm form = new LoginForm(name, pwd, 0);
        api.postData(Constants.getUrl(Constants.API_LOGIN_URL), form, loginListener, new CommonResponse.ResponseErrorListener() {
            @Override
            protected void onError() {
                super.onError();
                setEnabled(true);
            }
        }, TAG, UserInfoVo.class);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.cancelRequest(TAG);
    }

    @Override
    public void onBackPressed() {
        if (loading.getVisibility() == View.VISIBLE) {
            setEnabled(true);
        } else {
            super.onBackPressed();
        }
    }

    private void setEnabled(boolean isEnable) {
        if (!isEnable) {
            if (loading != null)
                loading.setVisibility(View.VISIBLE);
        } else {
            if (loading != null)
                loading.setVisibility(View.GONE);
        }
        if (etUserName != null) {
            etUserName.setEnabled(isEnable);
            etPassword.setEnabled(isEnable);
            btnLogin.setEnabled(isEnable);
            btnReg.setEnabled(isEnable);
        }
    }
    @Override
    protected boolean enableSliding() {
        return true;
    }
}
