package com.openapi.ks.myapp.ui.user;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.form.LoginForm;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.ui.base.app.BaseSingleActivity;

import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by openapi on 2016/8/13.
 */
public class LoginActivity extends BaseSingleActivity {
    private static final String TAG = "login_tag";
    private final Pattern namePattern = Pattern.compile("[a-zA-Z0-9]{3,20}");
    @BindView(R.id.ll_loading)
    LinearLayout loading;
    @BindView(R.id.et_username)
    EditText etUserName;
    @BindView(R.id.et_password)
    EditText etPassword;
    @BindView(R.id.btn_login)
    Button btnLogin;
    @BindView(R.id.btn_reg)
    Button btnReg;
    @BindView(R.id.btn_findpass)
    Button btnFindPass;
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

    @OnClick(R.id.btn_reg)
    public void reg(View view) {
        finish();
        startActivity(RegActivity.class);
    }
    @OnClick(R.id.btn_findpass)
    public void tnFindPass(View view) {
        finish();
        startActivity(FindPassActivity.class);
    }
    @OnClick(R.id.btn_login)
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
}