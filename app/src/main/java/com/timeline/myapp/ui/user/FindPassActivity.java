package com.timeline.myapp.ui.user;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.vpn.R;
import com.timeline.myapp.bean.form.RegForm;
import com.timeline.myapp.bean.vo.NullReturnVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.BaseService;
import com.timeline.myapp.ui.base.app.BaseSingleActivity;

import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2016/8/13.
 */
public class FindPassActivity extends BaseSingleActivity {
    private static final String TAG = "findpass_tag";
    private final Pattern namePattern = Pattern.compile("[a-zA-Z0-9]{3,20}");
    @BindView(R.id.ll_loading)
    LinearLayout loading;
    @BindView(R.id.et_email)
    EditText etEmail;
    @BindView(R.id.btn_findpass)
    Button btnFindPass;
    BaseService baseService;
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            ToastUtil.showShort(R.string.email_see);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_findpass);
        setToolbarTitle(R.string.findpass, true);
        baseService = new BaseService();
        baseService.setup(this);
    }
    @OnClick(R.id.btn_findpass)
    public void tnFindPass(View view) {
        String email = etEmail.getText().toString();
        if (!email.contains("@")) {
            ToastUtil.showShort(R.string.empty_name_pwd);
            return;
        }
        setEnabled(false);
        RegForm form = new RegForm(null, null, null, null,email);
        baseService.postData(Constants.getUrl(Constants.API_FINDPASS_URL), form, loginListener, new CommonResponse.ResponseErrorListener() {
            @Override
            protected void onError() {
                super.onError();
                setEnabled(true);
            }
        }, TAG, NullReturnVo.class);

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
        if (etEmail != null) {
            etEmail.setEnabled(isEnable);
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
