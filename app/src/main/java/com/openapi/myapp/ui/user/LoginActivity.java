package com.openapi.myapp.ui.user;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.StringUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.yewu.net.request.CommonResponse;
import com.openapi.myapp.bean.form.LoginForm;
import com.openapi.myapp.bean.vo.UserInfoVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.myapp.ui.base.app.BaseSingleActivity;

import java.util.regex.Pattern;

/**
 * Created by dengt on 2016/8/13.
 */
public class LoginActivity extends BaseSingleActivity {
    private static final String TAG = "login_tag";
    private final Pattern namePattern = Pattern.compile("[a-zA-Z0-9]{3,20}");
    LinearLayout loading;
    EditText etUserName;
    EditText etPassword;
    Button btnLogin;
    Button btnReg;
    BaseService baseService;
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<UserInfoVo>() {
        @Override
        public void onResponse(UserInfoVo vo) {
            PreferenceUtils.setPrefInt(LoginActivity.this, Constants.SCORE_TMP, 0);
            UserLoginUtil.login(LoginActivity.this, vo);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_login);
        setToolbarTitle(R.string.login, true);
        loading = findViewById(R.id.ll_loading);
        etUserName = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnReg = findViewById(R.id.btn_reg);
        findViewById(R.id.btn_reg).setOnClickListener(this::reg);
        findViewById(R.id.btn_login).setOnClickListener(this::login);
        baseService = new BaseService();
        baseService.setup(this);
        String name = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_LAST, null);
        if (!StringUtils.isEmpty(name)) {
            etUserName.setText(name);
        }
        String pwd = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_PW_LAST, null);
        if (!StringUtils.isEmpty(pwd)) {
            etPassword.setText(pwd);
        }

    }

    public void reg(View view) {
        finish();
        startActivity(RegActivity.class);
    }
    public void login(View view) {
        String name = etUserName.getText().toString();
        String pwd = etPassword.getText().toString();
        if (StringUtils.isEmpty(name) || StringUtils.isEmpty(pwd)) {
            ToastUtil.showShort(R.string.empty_name_pwd);
            return;
        }
        if (!namePattern.matcher(name).matches()) {
            ToastUtil.showShort(R.string.error_pattern_name);
            return;
        }
        setEnabled(false);
        PreferenceUtils.setPrefString(this, Constants.LOGIN_USER_LAST, name);
        PreferenceUtils.setPrefString(this, Constants.LOGIN_USER_PW_LAST, pwd);
        int score = PreferenceUtils.getPrefInt(this, Constants.SCORE_TMP, 0);
        LoginForm form = new LoginForm(name, pwd, score);
        baseService.postData(Constants.getUrl(Constants.API_LOGIN_URL), form, loginListener, new CommonResponse.ResponseErrorListener() {
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
        baseService.cancelRequest(TAG);
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
    @Override
    public boolean needShow() {
        return false;
    }

    /** 忘记密码：进入找回密码页面。 */
    public void onForgotPassword(View view) {
        startActivity(new Intent(this, com.openapi.myapp.ui.account.PasswordActivity.class)
                .putExtra(com.openapi.myapp.ui.account.PasswordActivity.EXTRA_MODE,
                        com.openapi.myapp.ui.account.PasswordActivity.MODE_FORGOT));
    }
}
