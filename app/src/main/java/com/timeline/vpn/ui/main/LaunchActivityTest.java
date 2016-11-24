package com.timeline.vpn.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.StringUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.task.UpdateUserTask;
import com.timeline.vpn.ui.base.LogActivity;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by gqli on 2016/3/22.
 */
public class LaunchActivityTest extends LogActivity {
    @Bind(R.id.rl_spread)
    RelativeLayout ivAds;
    @Bind(R.id.et_text)
    EditText etText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_launch_spread_test);
        AdsAdview.init(this);
        ButterKnife.bind(this);
        UpdateUserTask.start(this);
    }
    @OnClick(R.id.bt_text_lunch)
    public void launch(View view) {
        Intent intent = new Intent(this, MainFragment.class);
        startActivity(intent);
        finish();
    }
    @OnClick(R.id.bt_text)
    public void onSave(View view){
        String url = etText.getText().toString();
        if(StringUtils.hasText(url)){
            PreferenceUtils.setPrefString(this,"IP",url);
            Constants.BASE_IP = url;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        String ip = PreferenceUtils.getPrefString(this,"IP",Constants.BASE_IP);
        Constants.BASE_IP = ip;
        etText.setText(ip);
        MobAgent.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPause(this);
    }

    @Override
    public void onDestroy() {
        ButterKnife.unbind(this);
        super.onDestroy();
    }
}
