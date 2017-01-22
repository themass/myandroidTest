package com.timeline.vpn.ui.feedback;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.alibaba.sdk.android.feedback.impl.FeedbackAPI;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.log.LogActivity;

import java.util.concurrent.Callable;

import butterknife.Bind;
import butterknife.ButterKnife;

public class ConversationDetailActivity extends LogActivity {
    @Nullable
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.tv_title)
    TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //建议放在此处做初始化,因为本身消耗性能
        FeedbackAPI.init(MyApplication.getInstance(), Constants.DEFAULT_FEEDBACK_APPKEY);
        setContentView(R.layout.base_toobar_activity);
        if (savedInstanceState == null) {
            final FragmentManager fm = getSupportFragmentManager();
            final FragmentTransaction transaction = fm.beginTransaction();
            final Fragment feedback = FeedbackAPI.getFeedbackFragment();
            FeedbackAPI.setFeedbackFragment(new Callable() {
                @Override
                public Object call() throws Exception {
                    transaction.replace(R.id.fl_body, feedback);
                    transaction.commit();
                    return null;
                }
            }, null);
        }
        ButterKnife.bind(this);
        setToolbarTitle(R.string.feed_back);
    }

    public void setToolbarTitle(int id) {
        tvTitle.setText(id);
        tvTitle.setTextColor(getResources().getColorStateList(R.color.base_white));
        setNavigationOut();
        MenuItem menuWanna = toolbar.getMenu().findItem(R.id.menu_view);
        menuWanna.setActionView(R.layout.common_image_view);
        ImageView ivMenu = (ImageView) menuWanna.getActionView().findViewById(R.id.iv_menu);
        ivMenu.setImageResource(R.drawable.ic_menu_wanna);
        menuWanna.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IWannaFragment.startFragment(ConversationDetailActivity.this);
            }
        });
    }

    public void setNavigationOut() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        toolbar.inflateMenu(R.menu.menu_space);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FeedbackAPI.cleanFeedbackFragment();
        FeedbackAPI.cleanActivity();
    }
}
