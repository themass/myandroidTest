package com.qq.myapp.ui.user;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.qq.e.comm.util.StringUtil;
import com.qq.kuaibo.R;
import com.sspacee.common.ui.view.MyEditText;
import com.sspacee.common.ui.view.SiteAutoCompleteTextView;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.PathUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.net.VolleyUtils;
import com.sspacee.yewu.net.request.CommonResponse;

import com.qq.myapp.bean.form.CustomeAddForm;
import com.qq.myapp.bean.vo.NullReturnVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.BaseService;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.data.config.CustomeAddEvent;
import com.qq.myapp.data.urlparser.CustomeAddFormParser;
import com.qq.myapp.ui.base.app.BaseSingleActivity;

import butterknife.BindView;
import butterknife.OnClick;


/**
 * Created by themass on 2016/3/17.
 */
public class AddCustomeInfoActivity extends BaseSingleActivity {
    public static final String PARAM = "CUSTOMEPARA";
    private static final String TAG = "customeadd_tag";
    @BindView(R.id.ll_loading)
    LinearLayout loading;
    @BindView(R.id.et_title)
    MyEditText etTitle;
    @BindView(R.id.et_url)
    SiteAutoCompleteTextView etUrl;
    @BindView(R.id.open_mode)
    RadioGroup radioGroup;
    @BindView(R.id.open_schema)
    RadioGroup radioSchemaGroup;
    @BindView(R.id.http)
    RadioButton radioSchemaHttp;
    @BindView(R.id.https)
    RadioButton radioSchemaHttps;
    @BindView(R.id.open_local)
    RadioButton radioOpenLocal;
    @BindView(R.id.open_browser)
    RadioButton radioOpenBrowser;
    BaseService baseService;
    private CustomeAddForm form;
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            EventBusUtil.getEventBus().post(new CustomeAddEvent(form.id, form.title, form.url, null));
            ToastUtil.showShort(R.string.custome_ok);
            finish();
        }
    };

    public static void startActivity(Context context, CustomeAddForm form) {
        if (UserLoginUtil.getUserCache() != null) {
            Intent intent = new Intent(context, AddCustomeInfoActivity.class);
            if (form != null) {
                intent.putExtra(PARAM, form);
            }
            context.startActivity(intent);
        } else {
            ToastUtil.showShort(R.string.need_login);
            context.startActivity(new Intent(context, LoginActivity.class));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_add_custome);
        setToolbarTitle(R.string.custome_title, true);
        baseService = new BaseService();
        baseService.setup(this);
        form = (CustomeAddForm) getIntent().getSerializableExtra(PARAM);
        if (form == null) {
            form = new CustomeAddForm();
        } else {
            form = CustomeAddFormParser.parser(form.url, form.id, form.title);
        }
        etTitle.setText(form.title);
        if (StringUtils.hasText(form.uri)) {
            etUrl.setText(form.uri);
            if (Constants.HTTP_URL.equals(form.schema)) {
                radioSchemaHttp.setChecked(true);
            } else {
                radioSchemaHttps.setChecked(true);
            }
            if (Constants.OpenUrlPath.browser == form.openPath) {
                radioOpenBrowser.setChecked(true);
            } else {
                radioOpenLocal.setChecked(true);
            }
        }

    }

    @OnClick(R.id.btn_save)
    public void save(View view) {
        form.title = etTitle.getText().toString();
        form.uri = etUrl.getText().toString();
        int id = radioGroup.getCheckedRadioButtonId();
        int id1 = radioSchemaGroup.getCheckedRadioButtonId();
        if (id == -1 || StringUtil.isEmpty(form.title) || StringUtil.isEmpty(form.uri)) {
            ToastUtil.showShort( R.string.empty_info);
            return;
        }
        if (radioSchemaGroup.getCheckedRadioButtonId() == R.id.http) {
            form.schema = Constants.HTTP_URL;
        } else {
            form.schema = Constants.HTTPS_URL;
        }
        if(!form.uri.startsWith(Constants.HTTP_URL)) {
            form.url = form.schema + Constants.URL_TMP + form.uri;
        }else{
            form.url = form.uri;
        }
        if (radioGroup.getCheckedRadioButtonId() == R.id.open_browser) {
            form.url = PathUtil.getLocalOpenUrl(form.url);
        }
        setEnabled(false);
        baseService.postData(Constants.getUrl(Constants.API_ADD_CUSTOME), form, listener, new CommonResponse.ResponseErrorListener() {
            @Override
            protected void onError() {
                super.onError();
                setEnabled(true);
            }
        }, TAG, NullReturnVo.class);
    }

    private void setEnabled(boolean isEnable) {
        if (!isEnable) {
            loading.setVisibility(View.VISIBLE);
        } else {
            loading.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (loading.getVisibility() == View.VISIBLE) {
            setEnabled(true);
            VolleyUtils.cancelRequest(TAG);
        } else {
            super.onBackPressed();
        }
    }

    protected boolean enableSliding() {
        return true;
    }
}
