package com.timeline.vpn.ui.main;

import android.content.Intent;
import android.os.AsyncTask;
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
import com.timeline.vpn.bean.vo.AdsStrategyVo;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.config.ConfigActionJump;
import com.timeline.vpn.service.CharonVpnService;
import com.timeline.vpn.ui.base.BaseDrawerActivity;
import com.timeline.vpn.ui.maintab.OnBackKeyUpListener;
import com.timeline.vpn.ui.maintab.TabAdsFragment;
import com.timeline.vpn.ui.maintab.TabEnergyFragment;
import com.timeline.vpn.ui.maintab.TabIndexFragment;
import com.umeng.fb.FeedbackAgent;
import com.umeng.message.PushAgent;

/**
 * Created by gqli on 2016/3/1.
 */
public class MainFragment extends BaseDrawerActivity implements TabHost.OnTabChangeListener{
    private FragmentTabHost mTabHost;
    private TabWidget mainTab;
    private long firstTime = 0;
    private boolean pendingIntroAnimation;
    private OnBackKeyUpListener keyListener;
    private BaseService indexService;
    private static final String ADS_TAG = "ADS_TAG";
    FeedbackAgent fb;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_fragment);
        setupView();
        EventBusUtil.getEventBus().register(new ConfigActionJump());
        setUpUmengFeedback();
        setUpAds();
    }
    private void setUpAds(){
        indexService = new BaseService();
        indexService.setup(this);
        new AsyncTask<String,Integer,Boolean>() {
            @Override
            protected Boolean doInBackground(String ... params){
                AdsStrategyVo vo = indexService.getData(Constants.ADSSTRATEGY_URL, AdsStrategyVo.class,ADS_TAG);
                if(vo!=null){
                    PreferenceUtils.setPrefObj(MainFragment.this,Constants.STORY_ADSSTATEGY,vo);
                }
                return Boolean.TRUE;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    public void setListener(OnBackKeyUpListener keyListener){
        this.keyListener = keyListener;
    }
    private void setupView() {
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getFragmentManager(), R.id.realtabcontent);
        mTabHost.setOnTabChangedListener(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        addTab(inflater, R.string.tab_tag_index, TabIndexFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_index);
        addTab(inflater, R.string.tab_tag_power, TabEnergyFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_power);
        addTab(inflater, R.string.tab_tag_ads, TabAdsFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_ads);
        mTabHost.getTabWidget().setDividerDrawable(null);
        mainTab = mTabHost.getTabWidget();
        startService(new Intent(this, CharonVpnService.class));
    }

    /**
     * //友盟feedback
     */
    private void setUpUmengFeedback() {
        fb = new FeedbackAgent(this);
        fb.sync();
        fb.openAudioFeedback();
        fb.openFeedbackPush();
        PushAgent.getInstance(this).enable();
        new AsyncTask<String,Integer,Boolean>() {
            @Override
            protected Boolean doInBackground(String ... params){
                boolean result = fb.updateUserInfo();
                return Boolean.TRUE;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

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
        super.onDestroy();
        stopService(new Intent(this, CharonVpnService.class));
    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK) {
            if(keyListener!=null){
                keyListener.onkeyBackUp();
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
}
