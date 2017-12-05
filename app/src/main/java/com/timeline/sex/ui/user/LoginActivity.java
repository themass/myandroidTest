package com.timeline.sex.ui.user;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.qq.e.comm.util.StringUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.sex.R;
import com.timeline.sex.bean.form.LoginForm;
import com.timeline.sex.bean.vo.UserInfoVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.BaseService;
import com.timeline.sex.data.UserLoginUtil;
import com.timeline.sex.ui.base.app.BaseSingleActivity;

import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2016/8/13.
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
        if (!StringUtil.isEmpty(name)) {
            etUserName.setText(name);
        }
        String pwd = PreferenceUtils.getPrefString(this, Constants.LOGIN_USER_PW_LAST, null);
        if (!StringUtil.isEmpty(pwd)) {
            etPassword.setText(pwd);
        }

    }

    @OnClick(R.id.btn_reg)
    public void reg(View view) {
        finish();
        startActivity(RegActivity.class);
    }

    @OnClick(R.id.btn_login)
    public void login(View view) {
        String name = etUserName.getText().toString();
        String pwd = etPassword.getText().toString();
        if (StringUtil.isEmpty(name) || StringUtil.isEmpty(pwd)) {
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
