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
import com.timeline.myapp.ui.fragment.LocationChooseFragment;
import com.timeline.vpn.R;

/**
 * Created by themass on 2016/3/1.
 */
public abstract class BaseWeatherMenuActivity extends LogActivity {
    public ImageView ivLocation;

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
            LogUtil.i("onCreateOptionsMenu");
        }
        MenuItem menuLocation = menu.findItem(R.id.menu_location);
        if(!showLoc()){
            menuLocation.setVisible(false);
        }
        else{
            menuLocation.setActionView(R.layout.common_actionbar_image_view);
            ivLocation = (ImageView) menuLocation.getActionView().findViewById(R.id.iv_menu);
            menuLocation.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LogUtil.i("menuLocation Click");
                    LocationChooseFragment.startFragment(BaseWeatherMenuActivity.this);
                }
            });
            setupLocationIcon();
        }
        return true;
    }
    protected boolean showLoc(){
        return true;
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
