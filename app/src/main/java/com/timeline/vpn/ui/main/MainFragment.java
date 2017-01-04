package com.timeline.vpn.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v13.app.FragmentTabHost;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.ConfigActionJump;
import com.timeline.vpn.data.config.LogAddTofile;
import com.timeline.vpn.service.LogUploadService;
import com.timeline.vpn.ui.base.BaseDrawerActivity;
import com.timeline.vpn.ui.maintab.OnBackKeyUpListener;
import com.timeline.vpn.ui.maintab.TabIndexFragment;
import com.timeline.vpn.ui.maintab.TabVipFragment;
import com.umeng.message.PushAgent;
import com.umeng.message.UTrack;

import org.strongswan.android.logic.CharonVpnService;

/**
 * Created by themass on 2016/3/1.
 */
public class MainFragment extends BaseDrawerActivity implements TabHost.OnTabChangeListener {
    private static final String ADS_TAG = "ADS_TAG";
    private FragmentTabHost mTabHost;
    private TabWidget mainTab;
    private long firstTime = 0;
    private boolean pendingIntroAnimation;
    private OnBackKeyUpListener keyListener;
    private ConfigActionJump jump = new ConfigActionJump();
    private LogAddTofile logAdd = new LogAddTofile();
    private MyReceiver myReceiver;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_fragment);
        setupView();
        EventBusUtil.getEventBus().register(jump);
        EventBusUtil.getEventBus().register(logAdd);
        myReceiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MyApplication.UPDATE_STATUS_ACTION);
        registerReceiver(myReceiver, filter);
        AdsAdview.init(this);
    }

    public void setListener(OnBackKeyUpListener keyListener) {
        this.keyListener = keyListener;
    }

    private void setupView() {
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getFragmentManager(), R.id.realtabcontent);
        mTabHost.setOnTabChangedListener(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        addTab(inflater, R.string.tab_tag_index, TabIndexFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_index);
        addTab(inflater, R.string.tab_tag_vip, TabVipFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_vip);
//        addTab(inflater, R.string.tab_tag_ads, TabAdsFragment.class,
//                R.drawable.ac_bg_tab_index, R.string.tab_ads);
        mTabHost.getTabWidget().setDividerDrawable(null);
        mainTab = mTabHost.getTabWidget();
        startService(CharonVpnService.class);
    }

    private View addTab(LayoutInflater inflater, int tag, Class clss,
                        int icon, int title) {
        View indicator = inflater.inflate(R.layout.main_tab_widget_item_layout,
                mTabHost.getTabWidget(), false);
        ImageView imgView = (ImageView) indicator.findViewById(R.id.navi_icon);
        TextView titleView = (TextView) indicator.findViewById(R.id.navi_title);
        imgView.setImageResource(icon);
        titleView.setText(title);
        mTabHost.addTab(mTabHost.newTabSpec(getString(tag)).setIndicator(indicator), clss,
                null);
        return indicator;
    }

    @Override
    public void onTabChanged(String tabId) {

    }

    @Override
    public void onDestroy() {
        LogUtil.i("main destory");
        startService(CharonVpnService.class);
        stopService(CharonVpnService.class);
//        stopService(LogToFileService.class);
        stopService(LogUploadService.class);
        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(logAdd);
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyListener != null) {
                keyListener.onkeyBackUp();
            }
            if(CharonVpnService.VPN_STATUS_NOTIF) {
                moveTaskToBack(true);
                return true;
            }
            long secondTime = System.currentTimeMillis();
            if (secondTime - firstTime > 2000) {
                Toast.makeText(this, R.string.close_over, Toast.LENGTH_SHORT).show();
                firstTime = secondTime;//更新firstTime
                return true;
            } else {                                                    //两次按键小于2秒时，退出应用
                super.onKeyUp(keyCode, event);
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    public void appInfo(){
        LogUtil.i("appInfo");
        UserInfoVo user = UserLoginUtil.getUserCache();
        if(user==null){
            String name = PreferenceUtils.getPrefString(MainFragment.this, Constants.LOGIN_USER_LAST,null);
            if(name!=null)
                PushAgent.getInstance(MainFragment.this).removeAlias(name, Constants.MY_PUSH_TYPE, new UTrack.ICallBack(){
                    @Override
                    public void onMessage(boolean isSuccess, String message) {
                        if(!isSuccess)
                            LogUtil.e("removeAlias:false;message:"+message);
                    }
                });
        }else{
            PushAgent.getInstance(MainFragment.this).addAlias(user.name, Constants.MY_PUSH_TYPE, new UTrack.ICallBack() {
                @Override
                public void onMessage(boolean isSuccess, String message) {
                    if(!isSuccess)
                        LogUtil.e("addAlias:false;message:"+message);
                }
            });
        }

    }
    class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            appInfo();
        }
    }
}
