package com.timeline.vpn.ui.base;

import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.timeline.vpn.R;
import com.timeline.vpn.strangswan.ui.VpnManagerActivity;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 15.07.15.
 */
public class BaseDrawerActivity extends BaseToolBarActivity {

    @Nullable
    @Bind(R.id.drawerLayout)
    DrawerLayout drawerLayout;
    @Nullable
    @Bind(R.id.ivMenuUserProfilePhoto)
    ImageView ivMenuUserProfilePhoto;

    private int avatarSize;
    private String profilePhoto;
    private MenuItem vpnMenuItem;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.activity_drawer);
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.flContentRoot);
        LayoutInflater.from(this).inflate(layoutResID, viewGroup, true);
        bindViews();
        setupToolbar();
    }

    @Override
    protected void setupToolbar() {
        super.setupToolbar();
        if (getToolbar() != null) {
            getToolbar().setNavigationIcon(R.drawable.ic_menu_drawer);
            getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
            });
        }
    }
    String url = "http://api.money.126.net/data/feed/0000001";
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_vpn, menu);
        vpnMenuItem = menu.findItem(R.id.action_menu_vpn_setting);
        vpnMenuItem.setActionView(R.layout.menu_item_view);
        vpnMenuItem.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                startService(CharonVpnService.class);
                startActivity(VpnManagerActivity.class);
            }
        });
        return true;
    }
    public MenuItem getVpnMenuItem() {
        return vpnMenuItem;
    }

}
