package com.qq.vpn.ui.base.actvity;

import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.qq.Constants;
import com.qq.MyApplication;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.ShareUtil;
import com.qq.ext.util.StringUtils;
import com.qq.vpn.main.login.SinginActivity;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.support.MyUrlUtil;
import com.qq.network.R;
import com.qq.vpn.support.UserLoginUtil;
import com.qq.vpn.support.config.UserLoginEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;

/**
 * Created by dengt on 14-03-12.
 */
public class BaseDrawerMenuActivity extends ToolBarActivity {
    @Nullable
    @BindView(R.id.drawerLayout)
    public DrawerLayout drawerLayout;
    @Nullable
    @BindView(R.id.nv_content)
    NavigationView nvDrawer;
    View headerView;
    NetApiUtil api;
    MenuItem miLogout;
    MenuItem miLogin;
    TextView tvName;
    TextView tvScore;
    @Override
    public void setContentView(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.common_menu_drawer);
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.fl_content_root);
        LayoutInflater.from(this).inflate(layoutResID, viewGroup, true);
        bindViews();
        setupToolbar();
        headerView = nvDrawer.getHeaderView(0);
        tvName = (TextView) headerView.findViewById(R.id.name);
        tvScore = (TextView) headerView.findViewById(R.id.score);
        miLogout = nvDrawer.getMenu().findItem(R.id.menu_signout);
        miLogin = nvDrawer.getMenu().findItem(R.id.menu_signin);
        nvDrawer.setItemIconTintList(null);
        api = new NetApiUtil(this);
        loginEvent(null);
    }
    public void onAbout(View view) {
        MyUrlUtil.showAbout(this);
    }
    public boolean closeDrawer() {
        if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
            drawerLayout.closeDrawer(Gravity.LEFT);
            return true;
        }
        return false;
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void loginEvent(UserLoginEvent event){
        LogUtil.i("loginEvent");
        if(UserLoginUtil.getUserCache()!=null){
            miLogout.setVisible(true);
            miLogin.setVisible(false);
            tvName.setVisibility(View.VISIBLE);
            tvName.setText(UserLoginUtil.getUserCache().name);
            tvScore.setVisibility(View.VISIBLE);
            tvScore.setText(UserLoginUtil.getUserCache().score+"积分");
        }else{
            miLogout.setVisible(false);
            miLogin.setVisible(true);
            tvName.setVisibility(View.GONE);
            tvScore.setVisibility(View.GONE);
        }
    }
    @Override
    protected void setupToolbar() {
        super.setupToolbar();
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_m_drawer);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (!closeDrawer()) {
                            drawerLayout.openDrawer(Gravity.LEFT);
                        }
                    }catch(Exception e){
                        LogUtil.e(e);
                    }
                }
            });
        }
        nvDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
               if (item.getItemId() == R.id.menu_about) {
                    onAbout(null);
                }else if (item.getItemId() == R.id.menu_share) {
                   showShare();
               }else if (item.getItemId() == R.id.menu_signin) {
                   startActivity(SinginActivity.class);
               }else if (item.getItemId() == R.id.menu_signout) {
                   api.postData(Constants.getUrl(Constants.API_LOGOUT_URL), null, null, null, null, null);
                   UserLoginUtil.logout(BaseDrawerMenuActivity.this);
               }
                return false;
            }
        });
    }
    public void showShare() {
        ShareUtil util = new ShareUtil(this);
        util.shareText(null,null,Constants.SSPACEE+" 灯塔，照亮前进的路","灯塔","照亮前进的路");
    }

}
