package com.timeline.vpn.ui.user;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.config.TabChangeEvent;
import com.timeline.vpn.ui.base.app.BaseSingleActivity;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/13.
 */
public class SettingActivity extends BaseSingleActivity {
    @Bind(R.id.sw_area)
    Switch swArea;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_setting);
        setToolbarTitle(R.string.setting,true);
        swArea.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.AREA_SWITCH, true));
        swArea.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.AREA_SWITCH, isChecked);
                LogUtil.i("swArea: "+isChecked);
                EventBusUtil.getEventBus().post(new TabChangeEvent());
            }
        });
    }
}
