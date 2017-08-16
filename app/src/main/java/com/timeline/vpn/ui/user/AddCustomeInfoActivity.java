package com.timeline.vpn.ui.user;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.qq.e.comm.util.StringUtil;
import com.sspacee.common.ui.view.MyEditText;
import com.sspacee.common.ui.view.SiteAutoCompleteTextView;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.PathUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.net.VolleyUtils;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.form.CustomeAddForm;
import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.CustomeAddEvent;
import com.timeline.vpn.data.urlparser.CustomeAddFormParser;
import com.timeline.vpn.ui.base.app.BaseSingleActivity;

import butterknife.Bind;
import butterknife.OnClick;

import static com.kuaiyou.g.a.getActivity;

/**
 * Created by themass on 2016/3/17.
 */
public class AddCustomeInfoActivity extends BaseSingleActivity {
    private static final String TAG = "customeadd_tag";
    public static final String PARAM = "CUSTOMEPARA";
    @Bind(R.id.ll_loading)
    LinearLayout loading;
    @Bind(R.id.et_title)
    MyEditText etTitle;
    @Bind(R.id.et_url)
    SiteAutoCompleteTextView etUrl;
    @Bind(R.id.open_mode)
    RadioGroup radioGroup;
    @Bind(R.id.open_schema)
    RadioGroup radioSchemaGroup;
    @Bind(R.id.http)
    RadioButton radioSchemaHttp;
    @Bind(R.id.https)
    RadioButton radioSchemaHttps;
    @Bind(R.id.open_local)
    RadioButton radioOpenLocal;
    @Bind(R.id.open_browser)
    RadioButton radioOpenBrowser;
    BaseService baseService;
    private CustomeAddForm form;
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            EventBusUtil.getEventBus().post(new CustomeAddEvent(form.id,form.title, form.url, null));
            Toast.makeText(AddCustomeInfoActivity.this, R.string.custome_ok, Toast.LENGTH_SHORT).show();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_add_custome);
        setToolbarTitle(R.string.custome_title,true);
        baseService = new BaseService();
        baseService.setup(this);
        form = (CustomeAddForm)getIntent().getSerializableExtra(PARAM);
        if(form==null){
            form = new CustomeAddForm();
        }else {
            form = CustomeAddFormParser.parser(form.url,form.id,form.title);
        }
        etTitle.setText(form.title);
        if(StringUtils.hasText(form.uri)){
            etUrl.setText(form.uri);
            if(Constants.HTTP_URL.equals(form.schema)){
                radioSchemaHttp.setChecked(true);
            }else{
                radioSchemaHttps.setChecked(true);
            }
            if(Constants.OpenUrlPath.browser==form.openPath){
                radioOpenBrowser.setChecked(true);
            }else{
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
            Toast.makeText(this, R.string.empty_info, Toast.LENGTH_SHORT).show();
            return;
        }
        if (radioSchemaGroup.getCheckedRadioButtonId() == R.id.http) {
            form.schema = Constants.HTTP_URL;
        }else{
            form.schema = Constants.HTTPS_URL;
        }
        form.url = form.schema+Constants.URL_TMP+form.uri;
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
    public static void startActivity(Context context,CustomeAddForm form){
        if (UserLoginUtil.getUserCache() != null) {
            Intent intent = new Intent(context, AddCustomeInfoActivity.class);
            if(form!=null){
                intent.putExtra(PARAM,form);
            }
            getActivity().startActivity(intent);
        } else {
            Toast.makeText(context,R.string.need_login,Toast.LENGTH_SHORT).show();
            getActivity().startActivity(new Intent(context, LoginActivity.class));
        }
    }
}
