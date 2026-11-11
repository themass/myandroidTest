package com.openapi.ks.ui.main;

import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.openapi.ks.ui.maintab.TabLocalFragment;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PermissionHelper;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.yewu.ads.base.BaseRewardManger;
import com.openapi.yewu.ads.base.RewardInterface;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.ConnLogUtil;
import com.openapi.myapp.data.config.ConfigActionJump;
import com.openapi.myapp.data.config.TabChangeEvent;
import com.openapi.myapp.ui.base.app.BaseDrawerActivity;
import com.openapi.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.free1.R;
import com.openapi.ks.ui.maintab.TabCustomeFragment;
import com.openapi.ks.ui.maintab.VpnFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.strongswan.android.logic.CharonVpnService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainFragmentViewPage extends BaseDrawerActivity implements ActivityCompat.OnRequestPermissionsResultCallback,RewardInterface.OnAdmobRewardListener {


    /**
 * Created by dengt on 2016/3/1.
 */
    public List<ItemFragment> list = new ArrayList<>();
    public boolean init = false;
    private Set<OnBackKeyDownListener> keyListeners = new HashSet<>();
    private ConfigActionJump jump = new ConfigActionJump();
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private MyPagerAdapter myPagerAdapter;
    private String POSITION = "POSITION";
    private int index = 0;
    public BaseRewardManger admobRewardManger;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main_viewpage);
        // 不在 onCreate 里启动 CharonVpnService：App 从后台拉起时 Android 12+ 会禁止后台启动服务并崩溃。
        // 连接时由 VpnStateService 在用户操作的前台路径里启动它。
        EventBusUtil.getEventBus().register(jump);
        if (getIntent() != null && getIntent().getBooleanExtra(
                com.openapi.data.vpn.VpnNotificationReceiver.EXTRA_TOGGLE_ON_OPEN, false)) {
            // 等首页和雷达片段建好再执行，否则事件可能没有订阅者
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    com.openapi.data.vpn.VpnNotificationReceiver::requestToggle, 800);
        }
        admobRewardManger = new BaseRewardManger(this,this);
    }

    /** 首页已在前台时，通知按钮再次拉起首页走这里（singleTop），同样要执行一次连接切换。 */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(
                com.openapi.data.vpn.VpnNotificationReceiver.EXTRA_TOGGLE_ON_OPEN, false)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    com.openapi.data.vpn.VpnNotificationReceiver::requestToggle, 300);
        }
    }

    @Override
    public void onNoRewardAD(){
//        if(!admobRewardManger.next()) {
//            AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN1, false,AdsContext.AdsFrom.ADMOB,1);
//        }
        AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN1, false,AdsContext.AdsFrom.ADMOB,1);

    }

    @Override
    public void showReward() {
        super.showReward();
        admobRewardManger.showAd();
    }

    @Override
    public boolean needPingView(){
        return false;
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TabChangeEvent event) {
        initTabs();
        LogUtil.i("onEvent:initTabs");
        myPagerAdapter.notifyDataSetChanged();
    }

    public void addListener(OnBackKeyDownListener keyListener) {
        keyListeners.add(keyListener);
    }

    public void removeListener(OnBackKeyDownListener keyListener) {
        keyListeners.remove(keyListener);
    }

    public void setupView() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.vp_view);
        initTabs();
        ConnLogUtil.sendAllLog(this);
        // 冷启动时不弹插屏：开屏广告已经占了启动这一段，两个全屏广告连着出现会很糟。

        if(!PermissionHelper.checkPermissions(this)) {
            PermissionHelper.showPermit(this);
        }

    }

    private void initTabs() {
        list.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        addData(inflater, R.string.tab_tag_local, TabLocalFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_index, null, 1);
//        addData(inflater, R.string.tab_tag_index, VpnFragment.class,
//                R.drawable.ac_bg_tab_index, R.string.tab_index, null, 2);

        // 工具：网络体检、分应用代理等，从设置里移出，方便发现
        addData(inflater, R.string.tab_tag_tools, com.openapi.ks.ui.maintab.ToolsFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_tools, null, 2);
        addData(inflater, R.string.tab_tag_sites, TabCustomeFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_sites, null, 3);

        myPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(myPagerAdapter);
        mTabLayout.setupWithViewPager(mViewPager);//将TabLayout和ViewPager关联起来。
        mTabLayout.addOnTabSelectedListener(new ViewPagerOnTabSelectedListener(mViewPager));
        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            tab.setCustomView(myPagerAdapter.getTabView(i, (i == 0)));
        }
    }

    private void addData(LayoutInflater inflater, int tag, Class<? extends Fragment> clss,
                         int icon, int title, Bundle args, int abslIndex) {
        list.add(new ItemFragment(tag, clss, icon, title, args, abslIndex));

    }
    @Override
    protected boolean showLoc(){
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(POSITION, mTabLayout.getSelectedTabPosition());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mViewPager.setCurrentItem(savedInstanceState.getInt(POSITION));
    }

    @Override
    public void onDestroy() {
        LogUtil.i("main destory");
        stopService(CharonVpnService.class);
//        stopService(LogUploadService.class);
        EventBusUtil.getEventBus().unregister(jump);
        admobRewardManger.onAdDestroy();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        admobRewardManger.onAdPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        admobRewardManger.onAdResume();
        super.onResume();
    }

    /**
     * 两次返回的时间窗口（毫秒）。实例字段，不跨页面、不跨进程。
     * 原来 2000ms 太短：长按返回键时系统会持续发 KEYCODE_BACK 的重复事件，
     * 一次长按就能凑成"两次"，于是"点一下就退出了"。除了放宽到 3 秒，
     * 还要过滤 repeatCount（见下面）。
     */
    private static final long BACK_EXIT_WINDOW_MS = 3000L;
    private long lastBackPressedAt = 0L;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyUp");
        if (closeDrawer()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 长按返回键会连续产生 repeat 事件，只认第一次按下，否则一次长按就退出。
            if (event.getRepeatCount() > 0) {
                return true;
            }
            boolean flag = false;
            for (OnBackKeyDownListener l : keyListeners) {
                flag = flag || l.onkeyBackDown();
            }
            if (flag) {
                return true;
            }
            if (com.openapi.data.vpn.VpnNotifier.isConnected()) {
                moveTaskToBack(true);
                return true;
            }

            // 两次返回间隔 2 秒内才退出；超时后第一次的提示失效，重新计时
            long now = System.currentTimeMillis();
            if (now - lastBackPressedAt > BACK_EXIT_WINDOW_MS) {
                lastBackPressedAt = now;
                ToastUtil.showShort(getString(R.string.close_over));
                return true;
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public class ViewPagerOnTabSelectedListener implements TabLayout.OnTabSelectedListener {
        private final ViewPager mViewPager;

        public ViewPagerOnTabSelectedListener(ViewPager viewPager) {
            mViewPager = viewPager;
        }

        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            LogUtil.i("tab select:" + tab.getPosition());
            mViewPager.setCurrentItem(tab.getPosition());
            setToolbarTitle(getString(list.get(tab.getPosition()).title), false);
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {

        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
            // No-op
        }
    }

    //ViewPager适配器
    class MyPagerAdapter extends FragmentPagerAdapter {

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).abslIndex;
        }

        @Override
        public Fragment getItem(int position) {
            try {
                return list.get(position).clss.newInstance();
            } catch (Exception e) {
                LogUtil.e(e);
                return null;
            }
        }

        public View getTabView(int position, boolean selected) {
            View indicator = LayoutInflater.from(MainFragmentViewPage.this).inflate(R.layout.tab_widiget, null);
            ImageView imgView = (ImageView) indicator.findViewById(R.id.navi_icon);
            TextView titleView = (TextView) indicator.findViewById(R.id.navi_title);
            imgView.setImageResource(list.get(position).icon);
            titleView.setText(list.get(position).title);
            if (selected) {
                indicator.setSelected(true);
            }
            return indicator;
        }
    }

    class ItemFragment {
        public int tag;
        public Class<? extends Fragment> clss;
        public int icon;
        public int title;
        public Bundle args;
        public int abslIndex;

        public ItemFragment(int tag, Class<? extends Fragment> clss, int icon, int title, Bundle args, int abslIndex) {
            this.tag = tag;
            this.clss = clss;
            this.icon = icon;
            this.title = title;
            this.args = args;
            this.abslIndex = abslIndex;

        }
    }
}
