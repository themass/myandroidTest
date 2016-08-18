package com.timeline.vpn.ui.user;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.qq.e.comm.util.StringUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.form.LoginForm;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.util.BeanUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.BaseSingleActivity;

import butterknife.Bind;

/**
 * Created by gqli on 2016/8/13.
 */
public class LoginActivity extends BaseSingleActivity{
    private static final String TAG="login_tag";
    @Nullable
    @Bind(R.id.ll_loading)
    LinearLayout loading;
    @Nullable
    @Bind(R.id.et_username)
    EditText etUserName;
    @Nullable
    @Bind(R.id.et_password)
    EditText etPassword;
    @Nullable
    @Bind(R.id.btn_login)
    Button btnLogin;
    @Nullable
    @Bind(R.id.btn_reg)
    Button btnReg;
    BaseService baseService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);
        setToolbarTitle(R.string.login);
        baseService = new BaseService();
        baseService.setup(this);
    }
    public void reg(View view){
        finish();
        startActivity(RegActivity.class);
    }
    public void login(View view){
        String name = etUserName.getText().toString();
        String pwd = etPassword.getText().toString();
        if(StringUtil.isEmpty(name)||StringUtil.isEmpty(pwd)){
            Toast.makeText(this,R.string.empty_name_pwd,Toast.LENGTH_SHORT).show();
            return;
        }
        setEnabled(false);
        LoginForm form = new LoginForm(name,pwd);
        baseService.postData(Constants.LOGIN_URL, BeanUtil.convertBean(form),loginListener,new CommonResponse.ResponseErrorListener(){
            @Override
            protected void onError() {
                super.onError();
                setEnabled(true);
            }
        },TAG,UserInfoVo.class);
    }
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<UserInfoVo>() {
        @Override
        public void onResponse(UserInfoVo vo) {
            UserLoginUtil.login(LoginActivity.this,vo);
            finish();
        }
    };
    @Override
    public void onBackPressed() {
        if(loading.getVisibility()==View.VISIBLE){
            setEnabled(true);
        }else {
            super.onBackPressed();
        }
    }
    private void setEnabled(boolean isEnable){
        if(!isEnable){
            loading.setVisibility(View.VISIBLE);
        }else{
            loading.setVisibility(View.GONE);
        }
        etUserName.setEnabled(isEnable);
        etPassword.setEnabled(isEnable);
        btnLogin.setEnabled(isEnable);
        btnReg.setEnabled(isEnable);
    }
}
