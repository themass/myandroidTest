package com.openapi.myapp.ui.user;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.openapi.common.ui.view.MyEditText;
import com.openapi.common.ui.view.SiteAutoCompleteTextView;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.PathUtil;
import com.openapi.common.util.StringUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.yewu.net.VolleyUtils;
import com.openapi.yewu.net.request.CommonResponse;
import com.openapi.ks.free1.R;
import com.openapi.myapp.bean.form.CustomeAddForm;
import com.openapi.myapp.bean.vo.NullReturnVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.myapp.data.config.CustomeAddEvent;
import com.openapi.myapp.data.urlparser.CustomeAddFormParser;
import com.openapi.myapp.ui.base.app.BaseSingleActivity;

/**
 * Created by dengt on 2016/3/17.
 */
public class AddCustomeInfoActivity extends BaseSingleActivity {
    public static final String PARAM = "CUSTOMEPARA";
    private static final String TAG = "customeadd_tag";
    LinearLayout loading;
    MyEditText etTitle;
    SiteAutoCompleteTextView etUrl;
    RadioGroup radioGroup;
    RadioGroup radioSchemaGroup;
    RadioButton radioSchemaHttp;
    RadioButton radioSchemaHttps;
    RadioButton radioOpenLocal;
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
        loading = findViewById(R.id.ll_loading);
        etTitle = findViewById(R.id.et_title);
        etUrl = findViewById(R.id.et_url);
        radioGroup = findViewById(R.id.open_mode);
        radioSchemaGroup = findViewById(R.id.open_schema);
        radioSchemaHttp = findViewById(R.id.http);
        radioSchemaHttps = findViewById(R.id.https);
        radioOpenLocal = findViewById(R.id.open_local);
        radioOpenBrowser = findViewById(R.id.open_browser);
        findViewById(R.id.btn_save).setOnClickListener(this::save);
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

    public void save(View view) {
        form.title = etTitle.getText().toString();
        form.uri = etUrl.getText().toString();
        int id = radioGroup.getCheckedRadioButtonId();
        int id1 = radioSchemaGroup.getCheckedRadioButtonId();
        if (id == -1 || StringUtils.isEmpty(form.title) || StringUtils.isEmpty(form.uri)) {
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
