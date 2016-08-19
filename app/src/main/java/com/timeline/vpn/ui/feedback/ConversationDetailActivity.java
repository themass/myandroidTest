package com.timeline.vpn.ui.feedback;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.umeng.fb.FeedbackAgent;
import com.umeng.fb.fragment.FeedbackFragment;

import butterknife.Bind;
import butterknife.ButterKnife;

public class ConversationDetailActivity extends FragmentActivity{
    @Nullable
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.tvTitle)
    TextView tvTitle;
    private FeedbackFragment mFeedbackFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_feedback);
        if (savedInstanceState == null) {
            String id = new FeedbackAgent(this).getDefaultConversation().getId();
            LogUtil.i(id);
            mFeedbackFragment = FeedbackFragment.newInstance(id);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container,mFeedbackFragment)
                    .commit();
        }
        ButterKnife.bind(this);
        setToolbarTitle(R.string.feed_back);
    }
    public void setToolbarTitle(int id) {
        tvTitle.setText(id);
        tvTitle.setTextColor(getResources().getColorStateList(R.color.base_white));
        setNavigationOut();
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
    protected void onNewIntent(android.content.Intent intent) {
        mFeedbackFragment.refresh();
    }
}
