package com.qq.vpn.ui.base.actvity;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.qq.ext.util.EventBusUtil;
import com.qq.ext.util.LogUtil;
import com.qq.network.R;
import com.qq.vpn.support.ImagePhotoLoad;
import com.qq.vpn.support.LocationUtil;
import com.qq.vpn.support.config.LocationChooseEvent;
import com.qq.vpn.support.config.PingEvent;
import com.qq.vpn.ui.fragment.LocationPageViewFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Field;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by dengt on 14-03-12.
 */
public abstract class ToolBarActivity extends LogActivity {

    @Nullable
    @BindView(R.id.toolbar)
    public Toolbar toolbar;
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @Nullable
    @BindView(R.id.fl_body)
    ViewGroup flBody;
    private Unbinder unbinder;
    private ImageView ivLocation;
    private ImageView ivPing;
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.base_toobar_view);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_body), true);
        bindViews();
        setupToolbar();
    }

    public void setupView() {

    }

    protected void bindViews() {
        unbinder = ButterKnife.bind(this);
        EventBusUtil.getEventBus().register(this);
        setupView();
    }

    protected void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    public void setContentViewWithoutInject(int layoutResId) {
        super.setContentView(R.layout.base_toobar_view);
        getLayoutInflater().inflate(layoutResId, (ViewGroup) findViewById(R.id.fl_body), true);
    }
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LogUtil.i("onConfigurationChanged->" + newConfig.orientation);
    }
    public View getNaviButton() {
        Field field = null;
        try {
            field =Toolbar.class.getDeclaredField("mNavButtonView");
            field.setAccessible(true);
            return  (View) field.get(toolbar);
        } catch (NoSuchFieldException e) {
            LogUtil.e(e);
        } catch (IllegalAccessException e) {
            LogUtil.e(e);
        }
        return null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
        EventBusUtil.getEventBus().unregister(this);
    }
    protected boolean needLocationView(){
        return true;
    }
    protected boolean needPingView(){
        return false;
    }
    protected void setupLocationIcon(){
        LogUtil.i("setupLocationIcon");
        if(ivLocation!=null)
            ImagePhotoLoad.getCountryImage(this, ivLocation, LocationUtil.getSelectLocationIcon(this));
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(LocationChooseEvent event) {
        setupLocationIcon();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (ivLocation == null) {
            getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        }
        MenuItem location = menu.findItem(R.id.menu_location);
        MenuItem ping = menu.findItem(R.id.menu_ping);
        if(!needLocationView()){
            location.setVisible(false);
        }else{
            location.setVisible(true);
            location.setActionView(R.layout.base_image_view);
            ivLocation = (ImageView) location.getActionView().findViewById(R.id.iv_menu);
            location.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocationPageViewFragment.startFragment(ToolBarActivity.this);
                }
            });
            setupLocationIcon();
        }
        if(!needPingView()){
            ping.setVisible(false);
        } else{
            ping.setVisible(true);
            ping.setActionView(R.layout.base_image_view);
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

    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(ToolBarActivity.this, c);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
    }

    public void startService(Class<? extends Service> c) {
        Intent intent = new Intent(ToolBarActivity.this, c);
        startService(intent);
    }

    public void stopService(Class<? extends Service> c) {
        Intent intent = new Intent(ToolBarActivity.this, c);
        stopService(intent);
    }

    public void showToolbar(boolean show) {
        if (show) {
            toolbar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    public void setToolbarTitle(int id, boolean initNav) {
        tvTitle.setText(id);
        if (initNav)
            setNavigationOut();
    }

    public void setToolbarTitle(String title, boolean initNav) {
        tvTitle.setText(title);
        if (initNav)
            setNavigationOut();
    }

    public TextView getToolbarTitle() {
        return tvTitle;
    }

    public void setToolbarTitle(String title) {
        tvTitle.setText(title);
        setNavigationOut();
    }

    public void setNavigationOut() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
