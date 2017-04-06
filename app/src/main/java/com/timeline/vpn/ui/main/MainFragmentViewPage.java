package com.timeline.vpn.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.ConfigActionJump;
import com.timeline.vpn.ui.base.app.BaseDrawerActivity;
import com.timeline.vpn.ui.inte.OnBackKeyUpListener;
import com.timeline.vpn.ui.maintab.TabCustomeFragment;
import com.timeline.vpn.ui.maintab.TabVipFragment;
import com.timeline.vpn.ui.maintab.TabVpnFragment;
import com.umeng.message.PushAgent;
import com.umeng.message.UTrack;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2016/3/1.
 */
public class MainFragmentViewPage extends BaseDrawerActivity {
    public List<ItemFragment> list = new ArrayList<>();
    private long firstTime = 0;
    private OnBackKeyUpListener keyListener;
    private ConfigActionJump jump = new ConfigActionJump();
    private MyReceiver myReceiver;
    private Toast destoryToast = null;
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private MyPagerAdapter myPagerAdapter;
    private String POSITION = "POSITION";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main_viewpage);
        setupView();
        EventBusUtil.getEventBus().register(jump);
        myReceiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MyApplication.UPDATE_STATUS_ACTION);
        registerReceiver(myReceiver, filter);
        AdsAdview.init(this);
        UserLoginUtil.initData(this);
        destoryToast = Toast.makeText(this, R.string.close_over, Toast.LENGTH_SHORT);
    }

    public void setListener(OnBackKeyUpListener keyListener) {
        this.keyListener = keyListener;
    }

    private void setupView() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        LayoutInflater inflater = LayoutInflater.from(this);
        addData(inflater, R.string.tab_tag_index, TabVpnFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_index, null);
        addData(inflater, R.string.tab_tag_vip, TabVipFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_vip, null);
        addData(inflater, R.string.tab_tag_customer, TabCustomeFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_customer, null);
        mViewPager = (ViewPager) findViewById(R.id.vp_view);
        myPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(myPagerAdapter);
        mTabLayout.setupWithViewPager(mViewPager);//将TabLayout和ViewPager关联起来。
        mTabLayout.setOnTabSelectedListener(new ViewPagerOnTabSelectedListener(mViewPager));
        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            tab.setCustomView(myPagerAdapter.getTabView(i, (i == 0)));
        }

    }

    private void addData(LayoutInflater inflater, int tag, Class<? extends Fragment> clss,
                         int icon, int title, Bundle args) {
        list.add(new ItemFragment(tag, clss, icon, title, args));

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
        EventBusUtil.getEventBus().unregister(jump);
        if (myReceiver != null) {
            unregisterReceiver(myReceiver);
        }
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyListener != null) {
                keyListener.onkeyBackUp();
            }
            long secondTime = System.currentTimeMillis();
            if (secondTime - firstTime > 2000) {
                destoryToast.show();
                firstTime = secondTime;//更新firstTime
                return true;
            } else {
                //两次按键小于2秒时，退出应用
                destoryToast.cancel();
                super.onKeyUp(keyCode, event);
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    public void appInfo() {
        LogUtil.i("appInfo");
        UserInfoVo user = UserLoginUtil.getUserCache();
        if (user == null) {
            String name = PreferenceUtils.getPrefString(MainFragmentViewPage.this, Constants.LOGIN_USER_LAST, null);
            if (name != null)
                PushAgent.getInstance(MainFragmentViewPage.this).removeAlias(name, Constants.MY_PUSH_TYPE, new UTrack.ICallBack() {
                    @Override
                    public void onMessage(boolean isSuccess, String message) {
                        if (!isSuccess)
                            LogUtil.e("removeAlias:false;message:" + message);
                    }
                });
        } else {
            PushAgent.getInstance(MainFragmentViewPage.this).addAlias(user.name, Constants.MY_PUSH_TYPE, new UTrack.ICallBack() {
                @Override
                public void onMessage(boolean isSuccess, String message) {
                    if (!isSuccess)
                        LogUtil.e("addAlias:false;message:" + message);
                }
            });
        }

    }

    public class ViewPagerOnTabSelectedListener implements TabLayout.OnTabSelectedListener {
        private final ViewPager mViewPager;

        public ViewPagerOnTabSelectedListener(ViewPager viewPager) {
            mViewPager = viewPager;
        }

        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            LogUtil.i("select:" + tab.getPosition());
            mViewPager.setCurrentItem(tab.getPosition());
            setToolbarTitle(getString(list.get(tab.getPosition()).title),false);
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {

        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
            // No-op
        }
    }

    class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            appInfo();
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
            if(selected){
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

        public ItemFragment(int tag, Class<? extends Fragment> clss, int icon, int title, Bundle args) {
            this.tag = tag;
            this.clss = clss;
            this.icon = icon;
            this.title = title;
            this.args = args;

        }
    }
}
