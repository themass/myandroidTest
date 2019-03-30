package com.qq.vpn.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;

import com.qq.ads.base.AdmobRewardManger;
import com.qq.ads.base.AdsContext;
import com.qq.ads.base.AdsManager;
import com.qq.ext.util.DoubleClickExit;
import com.qq.ext.util.EventBusUtil;
import com.qq.MobAgent;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.ToastUtil;
import com.qq.vpn.main.ui.ItemFragment;
import com.qq.vpn.support.AdsPopStrategy;
import com.qq.vpn.support.ConnLogReport;
import com.qq.vpn.support.config.ActionConfigBus;
import com.qq.vpn.ui.base.actvity.BaseDrawerMenuActivity;
import com.qq.fq3.R;
import com.qq.vpn.main.tab.VpnFragment;

import org.strongswan.android.logic.CharonVpnService;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends BaseDrawerMenuActivity implements ActivityCompat.OnRequestPermissionsResultCallback ,AdmobRewardManger.OnAdmobRewardListener{


    /**
 * Created by dengt on 2016/3/1.
 */
    private List<ItemFragment> list = new ArrayList<>();
    private ViewPager mViewPager;
    private MyPagerAdapter myPagerAdapter;
    public AdmobRewardManger admobRewardManger;
    public ActionConfigBus configBus = new ActionConfigBus();
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main_viewpage);
        startService(CharonVpnService.class);
        startService(CharonVpnService.class);
        EventBusUtil.getEventBus().register(configBus);
        ConnLogReport.send(this);
    }
    public void setupView() {
        list.clear();
        list.add(new ItemFragment(R.string.tab_tag_index, VpnFragment.class, R.drawable.ic_m_space, R.string.tab_index, null, 1));
        mViewPager = (ViewPager) findViewById(R.id.vp_view);
        myPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(myPagerAdapter);
        myPagerAdapter.notifyDataSetChanged();
        AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN, false);
        admobRewardManger = new AdmobRewardManger(this,this);
    }
    @Override
    public void onNoRewardAD(){
        AdsPopStrategy.clickAdsShowBtn(this);
    }

    @Override
    public void showReward() {
        super.showReward();
        admobRewardManger.showAd();
    }
    @Override
    public void onDestroy() {
        stopService(CharonVpnService.class);
        EventBusUtil.getEventBus().unregister(configBus);
        admobRewardManger.onAdDestroy();
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
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
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyUp");
        if (closeDrawer()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (CharonVpnService.VPN_STATUS_NOTIF) {
                moveTaskToBack(true);
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
    }
}
