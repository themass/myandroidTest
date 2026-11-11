package com.openapi.feature.user.profile;

import com.openapi.feature.user.login.LoginActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.openapi.uikit.widget.MyEditText;
import com.openapi.uikit.widget.SiteAutoCompleteTextView;
import com.openapi.common.util.android.PathUtil;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.network.http.HttpClient;
import com.openapi.network.request.CommonResponse;
import com.openapi.feature.user.R;
import com.openapi.core.model.CustomeAddForm;
import com.openapi.core.model.NullReturnVo;
import com.openapi.core.config.Constants;
import com.openapi.network.http.BaseService;
import com.openapi.core.account.UserLoginUtil;
import com.openapi.feature.user.data.urlparser.CustomeAddFormParser;
import com.openapi.core.legacyui.app.BaseSingleActivity;
import com.openapi.feature.user.databinding.LayoutAddCustomeBinding;
import android.view.ViewGroup;



/**
 * Created by openapi on 2016/3/17.
 */
@SuppressWarnings("unused")
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
        CustomeAddForm tempForm;
        // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tempForm = getIntent().getSerializableExtra(PARAM, CustomeAddForm.class);
        } else {
            tempForm = (CustomeAddForm) getIntent().getSerializableExtra(PARAM);
        }
        form = tempForm;
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
            ToastUtil.showShort(R.string.empty_info);
            return;
        }
        if (radioSchemaGroup.getCheckedRadioButtonId() == R.id.http) {
            form.schema = Constants.HTTP_URL;
        } else {
            form.schema = Constants.HTTPS_URL;
        }
        if (!form.uri.startsWith(Constants.HTTP_URL)) {
            form.url = form.schema + Constants.URL_TMP + form.uri;
        } else {
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

    @Override
    protected void onBindContent(ViewGroup container) {
        super.onBindContent(container);
        LayoutAddCustomeBinding b = LayoutAddCustomeBinding.bind(contentRoot(container));
        loading = b.llLoading;
        etTitle = b.etTitle;
        etUrl = b.etUrl;
        radioGroup = b.openMode;
        radioSchemaGroup = b.openSchema;
        radioSchemaHttp = b.http;
        radioSchemaHttps = b.https;
        radioOpenLocal = b.openLocal;
        radioOpenBrowser = b.openBrowser;
        b.btnSave.setOnClickListener(this::save);
    }

    @Override
    protected void unbindViews() {
        loading = null;
        etTitle = null;
        etUrl = null;
        radioGroup = null;
        radioSchemaGroup = null;
        radioSchemaHttp = null;
        radioSchemaHttps = null;
        radioOpenLocal = null;
        radioOpenBrowser = null;
        super.unbindViews();
    }


    private void setEnabled(boolean isEnable) {
        if (!isEnable) {
            loading.setVisibility(View.VISIBLE);
        } else {
            loading.setVisibility(View.GONE);
        }
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        if (loading.getVisibility() == View.VISIBLE) {
            setEnabled(true);
            HttpClient.cancelRequest(TAG);
        } else {
            super.onBackPressed();
        }
    }

    protected boolean enableSliding() {
        return true;
    }
}
