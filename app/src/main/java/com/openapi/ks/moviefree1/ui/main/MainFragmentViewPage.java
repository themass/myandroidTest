package com.openapi.ks.moviefree1.ui.main;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.openapi.ks.moviefree1.ui.maintab.TabLocalFragment;
import com.openapi.commons.common.util.DoubleClickExit;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PermissionHelper;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.ConnLogUtil;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ConfigActionJump;
import com.openapi.ks.myapp.data.config.LogAddTofile;
import com.openapi.ks.myapp.data.config.TabChangeEvent;
import com.openapi.ks.myapp.service.LogUploadService;
import com.openapi.ks.myapp.ui.base.app.BaseDrawerActivity;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.moviefree1.R;

import com.openapi.ks.moviefree1.ui.maintab.TabCustomeFragment;
import com.openapi.ks.moviefree1.ui.maintab.TabMovieFragment;
import com.openapi.ks.moviefree1.ui.maintab.TabNightFragment;
import com.openapi.ks.moviefree1.ui.maintab.TabVpnFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by openapi on 2016/3/1.
 */
public class MainFragmentViewPage extends BaseDrawerActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    public List<ItemFragment> list = new ArrayList<>();
    public boolean init = false;
    private Set<OnBackKeyDownListener> keyListeners = new HashSet<>();
    private ConfigActionJump jump = new ConfigActionJump();
    private LogAddTofile logAdd = new LogAddTofile();
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private MyPagerAdapter myPagerAdapter;
    private String POSITION = "POSITION";
    private int index = 0;
    private static final String SETTING_TAG="SETTING_TAG";
    private static final String WITER_TAG="WITER_TAG";
    private PermissionHelper mPermissionHelper;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main_viewpage);
        EventBusUtil.getEventBus().register(jump);
        EventBusUtil.getEventBus().register(logAdd);
        mPermissionHelper = new PermissionHelper(this);
        EventBusUtil.getEventBus().register(this);
        mPermissionHelper.checkNeedPermissions();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TabChangeEvent event) {
        initTabs();
        LogUtil.i("onEvent:initTabs");
    }
    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
        mPermissionHelper.checkNeedPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPermissionHelper.checkNeedPermissions();
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
        AdsContext.showNext(MainFragmentViewPage.this);
        ConnLogUtil.sendAllLog(this);
    }

    private void initTabs() {
        list.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        if(MyApplication.isTemp) {
            addData(inflater, R.string.tab_tag_index, TabVpnFragment.class,
                    R.drawable.ac_bg_tab_index, R.string.tab_index, null, 1);
        }else {
            addData(inflater, R.string.tab_tag_movie, TabMovieFragment.class,
                    R.drawable.ac_bg_tab_index, R.string.tab_movie, null, 1);
        }


        if (PreferenceUtils.getPrefBoolean(this, Constants.AREA_SWITCH, true)) {
            addData(inflater, R.string.tab_tag_ng, TabNightFragment.class,
                    R.drawable.ac_bg_tab_index, R.string.tab_ng, null, 2);
            if(!MyApplication.isTemp) {
                addData(inflater, R.string.tab_tag_area, TabLocalFragment.class,
                        R.drawable.ac_bg_tab_index, R.string.tab_area, null, 3);
            }else {
                addData(inflater, R.string.tab_tag_movie, TabMovieFragment.class,
                        R.drawable.ac_bg_tab_index, R.string.tab_movie, null, 3);
            }
        }
        addData(inflater, R.string.tab_tag_customer, TabCustomeFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_customer, null, 4);

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
        stopService(LogUploadService.class);
        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(logAdd);
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyUp");
        if (closeDrawer()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            boolean flag = false;
            for (OnBackKeyDownListener l : keyListeners) {
                flag = flag || l.onkeyBackDown();
            }
            if (flag) {
                return true;
            }

            if (!DoubleClickExit.check()) {
                ToastUtil.showShort(getString(R.string.close_over));
                return true;
            } else {
                finish();
            }
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
            LogUtil.i("tab select:" + tab.getPosition()+"; tag ="+tab.getTag());
            mViewPager.setCurrentItem(tab.getPosition());
            setToolbarTitle(getString(list.get(tab.getPosition()).title), false);
            index = tab.getPosition();
            ItemFragment item = list.get(tab.getPosition());
            boolean areami = PreferenceUtils.getPrefBoolean(MainFragmentViewPage.this, Constants.AREA_MI_SWITCH, false);
            if(UserLoginUtil.getUserCache()!=null && areami && (item.tag==R.string.tab_tag_ng || item.tag==R.string.tab_tag_area)){
                LayoutInflater inflater = LayoutInflater.from(MainFragmentViewPage.this);
                ViewGroup miView = (ViewGroup)inflater.inflate(R.layout.layout_areami,null);
                final EditText etMi = (EditText)miView.findViewById(R.id.et_mi);
                AlertDialog dialog = null;
                AlertDialog.Builder builder = new AlertDialog.Builder(MainFragmentViewPage.this,R.style.mydialog);
                builder.setView(miView);
                builder.setCancelable(false);
                //添加确定按钮
                builder.setPositiveButton(R.string.del_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(etMi.getText()!=null && etMi.getText().equals(UserLoginUtil.getUserCache().areaMi)){
                            dialogInterface.dismiss();
                        }else{
                            ToastUtil.showShort(R.string.password_error);
                            initTabs();
                        }
                    }
                });
                builder.setNegativeButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ToastUtil.showShort(R.string.password_error);
                        initTabs();
                    }
                });
                dialog = builder.create();
                dialog.show();
            }
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
