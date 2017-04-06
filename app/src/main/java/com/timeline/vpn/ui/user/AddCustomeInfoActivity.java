package com.timeline.vpn.ui.user;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.qq.e.comm.util.StringUtil;
import com.sspacee.common.net.VolleyUtils;
import com.sspacee.common.net.request.CommonResponse;
import com.sspacee.common.ui.view.MyEditText;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.PathUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.form.CustomeAddForm;
import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.CustomeAddEvent;
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
    MyEditText etUrl;
    @Bind(R.id.open_mode)
    RadioGroup radioGroup;
    @Bind(R.id.open_schema)
    RadioGroup radioSchemaGroup;
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
            form = new CustomeAddForm(null,null,null);
        }
        etTitle.setText(form.title);
        etUrl.setText(form.url);
    }

    @OnClick(R.id.btn_save)
    public void save(View view) {

        form.title = etTitle.getText().toString();
        form.url = etUrl.getText().toString();
        int id = radioGroup.getCheckedRadioButtonId();
        int id1 = radioSchemaGroup.getCheckedRadioButtonId();
        if (id == -1 || StringUtil.isEmpty(form.title) || StringUtil.isEmpty(form.url)) {
            Toast.makeText(this, R.string.empty_info, Toast.LENGTH_SHORT).show();
            return;
        }
        if (radioSchemaGroup.getCheckedRadioButtonId() == R.id.http) {
            form.url = "http://"+form.url;
        }else{
            form.url = "https://"+form.url;
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
    public static void startActivity(Context context){
        if (UserLoginUtil.getUserCache() != null) {
            getActivity().startActivity(new Intent(context, AddCustomeInfoActivity.class));
        } else {
            Toast.makeText(context,R.string.need_login,Toast.LENGTH_SHORT).show();
            getActivity().startActivity(new Intent(context, LoginActivity.class));
        }
    }
}
