package com.timeline.myapp.ui.base.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.timeline.myapp.data.ImagePhotoLoad;
import com.timeline.myapp.data.LocationUtil;
import com.timeline.myapp.data.config.PingEvent;
import com.timeline.myapp.ui.fragment.LocationPageViewFragment;
import com.timeline.vpn.R;

/**
 * Created by themass on 2016/3/1.
 */
public abstract class BaseWeatherMenuActivity extends LogActivity {
    public ImageView ivLocation;
    public ImageView ivPing;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBusUtil.getEventBus().unregister(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (ivLocation == null) {
            getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        }
        MenuItem location = menu.findItem(R.id.menu_location);
        MenuItem ping = menu.findItem(R.id.menu_ping);
        if(!showLoc()){
            location.setVisible(false);
        }else{
            location.setVisible(true);
            location.setActionView(R.layout.common_actionbar_image_view);
            ivLocation = (ImageView) location.getActionView().findViewById(R.id.iv_menu);
            location.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocationPageViewFragment.startFragment(BaseWeatherMenuActivity.this);
                }
            });
            setupLocationIcon();
        }
        if(!needPingView()){
            ping.setVisible(false);
        } else{
            ping.setVisible(true);
            ping.setActionView(R.layout.common_actionbar_image_view);
            ivPing = (ImageView) ping.getActionView().findViewById(R.id.iv_menu);
            ivPing.setImageResource(R.drawable.ic_ping);
            ping.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EventBusUtil.getEventBus().post(new PingEvent());
                }
            });
            setupLocationIcon();
        }
        return true;
    }
    protected boolean showLoc(){
        return false;
    }
    protected boolean needPingView(){
        return false;
    }
    protected void setupLocationIcon(){
        if(ivLocation!=null)
            ImagePhotoLoad.getCountryImage(this, ivLocation, LocationUtil.getSelectLocationIcon(this));
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i("onOptionsMenuClosed");
    }
}