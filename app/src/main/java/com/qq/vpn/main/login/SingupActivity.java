package com.qq.vpn.main.login;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import com.qq.Constants;
import com.qq.ext.network.req.CommonResponse;
import com.qq.ext.util.ToastUtil;
import com.qq.network.R;
import com.qq.vpn.domain.req.RegForm;
import com.qq.vpn.domain.res.NullReturnVo;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.ui.base.actvity.BaseSingleActivity;

import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by dengt on 2016/8/13.
 */
public class SingupActivity extends BaseSingleActivity {
    private static final String TAG = "login_tag";

    @BindView(R.id.ll_loading)
    LinearLayout loading;
    @BindView(R.id.et_name)
    EditText etUserName;
    @BindView(R.id.et_pass)
    EditText etPassword;
    @BindView(R.id.et_repass)
    EditText etRePassword;
    @BindView(R.id.btn_singup)
    Button btnReg;
    @BindView(R.id.et_ref)
    EditText etRef;
    NetApiUtil api;
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            setEnabled(true);
            ToastUtil.showShort( R.string.singup_success);
            startActivity(SinginActivity.class);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_singup);
        setToolbarTitle(R.string.singup, true);
        api = new NetApiUtil(this);
    }
    @OnClick(R.id.btn_singup)
    public void reg(View view) {
        String name = etUserName.getText().toString();
        String pwd = etPassword.getText().toString();
        String repwd = etRePassword.getText().toString();
        if (!Constants.passPattern.matcher(pwd).matches()) {
            ToastUtil.showShort(R.string.pattern_pass_err);
            return;
        }
        if (!Constants.namePattern.matcher(name).matches()) {
            ToastUtil.showShort(R.string.pattern_name_err);
            return;
        }
        if (!pwd.equals(repwd)) {
            ToastUtil.showShort( R.string.repwd_err);
            return;
        }
        setEnabled(false);
        RegForm form = new RegForm(name, pwd, repwd, "","",etRef.getText().toString());
        api.postData(Constants.getUrl(Constants.API_REG_URL), form, loginListener, new CommonResponse.ResponseErrorListener() {
            @Override
            protected void onError() {
                super.onError();
                setEnabled(true);
            }
        }, TAG, NullReturnVo.class);
    }

    private void setEnabled(boolean isEnable) {
        if (loading != null) {
            if (!isEnable) {
                loading.setVisibility(View.VISIBLE);
            } else {
                loading.setVisibility(View.GONE);
            }
            etUserName.setEnabled(isEnable);
            etPassword.setEnabled(isEnable);
            etRePassword.setEnabled(isEnable);
            btnReg.setEnabled(isEnable);
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

    @Override
    protected boolean enableSliding() {
        return true;
    }
}
