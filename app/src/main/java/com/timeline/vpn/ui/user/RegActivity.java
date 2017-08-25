package com.timeline.vpn.ui.user;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.qq.e.comm.util.StringUtil;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.form.RegForm;
import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.ui.base.app.BaseSingleActivity;

import java.util.regex.Pattern;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by themass on 2016/8/13.
 */
public class RegActivity extends BaseSingleActivity {
    private static final String TAG = "login_tag";
    private final Pattern passPattern = Pattern.compile("[0-9A-Za-z]{6,10}");
    private final Pattern namePattern = Pattern.compile("[a-zA-Z0-9]{3,20}");
    @Bind(R.id.ll_loading)
    LinearLayout loading;
    @Bind(R.id.et_username)
    EditText etUserName;
    @Bind(R.id.et_password)
    EditText etPassword;
    @Bind(R.id.et_repassword)
    EditText etRePassword;
    @Bind(R.id.btn_reg)
    Button btnReg;
    @Bind(R.id.btn_login)
    Button btnLogin;
    @Bind(R.id.sex)
    RadioGroup radioGroup;
    @Bind(R.id.bt_code)
    Button btCode;
    @Bind(R.id.et_code)
    EditText etCode;
    BaseService baseService;
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            setEnabled(true);
            Toast.makeText(RegActivity.this, R.string.reg_success, Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_reg);
        setToolbarTitle(R.string.reg,true);
        baseService = new BaseService();
        baseService.setup(this);
    }

    @OnClick(R.id.bt_code)
    public void getCode(View view) {
        setEnabled(false);
    }

    @OnClick(R.id.btn_login)
    public void login(View view) {
        finish();
        startActivity(LoginActivity.class);
    }

    @OnClick(R.id.btn_reg)
    public void reg(View view) {
        String name = etUserName.getText().toString();
        String pwd = etPassword.getText().toString();
        String repwd = etRePassword.getText().toString();
        int id = radioGroup.getCheckedRadioButtonId();
        if (id == -1 || StringUtil.isEmpty(name) || StringUtil.isEmpty(pwd) || StringUtil.isEmpty(repwd)) {
            Toast.makeText(this, R.string.empty_name_pwd, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!passPattern.matcher(pwd).matches()) {
            Toast.makeText(this, R.string.error_pattern_pass, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!namePattern.matcher(name).matches()) {
            Toast.makeText(this, R.string.error_pattern_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pwd.equals(repwd)) {
            Toast.makeText(this, R.string.error_repwd, Toast.LENGTH_SHORT).show();
            return;
        }
        String sex = Constants.SEX_M;
        if (radioGroup.getCheckedRadioButtonId() == R.id.female) {
            sex = Constants.SEX_F;
        }
        setEnabled(false);
        RegForm form = new RegForm(name, pwd, repwd, sex);
        baseService.postData(Constants.getUrl(Constants.API_REG_URL), form, loginListener, new CommonResponse.ResponseErrorListener() {
            @Override
            protected void onError() {
                super.onError();
                setEnabled(true);
            }
        }, TAG, NullReturnVo.class);
    }

    private void setEnabled(boolean isEnable) {
        if(loading!=null) {
            if (!isEnable) {
                loading.setVisibility(View.VISIBLE);
            } else {
                loading.setVisibility(View.GONE);
            }
            etUserName.setEnabled(isEnable);
            etPassword.setEnabled(isEnable);
            etRePassword.setEnabled(isEnable);
            btnLogin.setEnabled(isEnable);
            btnReg.setEnabled(isEnable);
            etCode.setEnabled(isEnable);
            btCode.setEnabled(isEnable);
        }
    }

    @Override
    public void onBackPressed() {
        if (loading.getVisibility() == View.VISIBLE) {
            setEnabled(true);
        } else {
            super.onBackPressed();
        }
    }
    protected boolean enableSliding() {
        return true;
    }
}
