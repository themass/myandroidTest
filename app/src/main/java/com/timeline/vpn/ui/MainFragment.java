package com.timeline.vpn.ui;

import android.os.Bundle;
import android.support.v13.app.FragmentTabHost;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.BaseDrawerActivity;
import com.timeline.vpn.ui.maintab.TabIndexFragment;
import com.timeline.vpn.ui.maintab.TabNewsFragment;

/**
 * Created by gqli on 2016/3/1.
 */
public class MainFragment extends BaseDrawerActivity implements TabHost.OnTabChangeListener {
    private FragmentTabHost mTabHost;
    private TabWidget mainTab;
    private boolean pendingIntroAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_fragment);
        setupView();
    }

    private void setupView() {
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getFragmentManager(), R.id.realtabcontent);
        mTabHost.setOnTabChangedListener(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        addTab(inflater, R.string.tab_tag_index, TabIndexFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_index);
        addTab(inflater, R.string.tab_tag_news, TabNewsFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_news);
        addTab(inflater, R.string.tab_tag_happy, TabNewsFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_happy);
        addTab(inflater, R.string.tab_tag_more, TabNewsFragment.class,
                R.drawable.ic_tab_tech, R.string.tab_more);
        mTabHost.getTabWidget().setDividerDrawable(null);
        mainTab = mTabHost.getTabWidget();
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
}
