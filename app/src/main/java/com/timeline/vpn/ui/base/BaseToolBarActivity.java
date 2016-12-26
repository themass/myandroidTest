package com.timeline.vpn.ui.base;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.timeline.vpn.R;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by Miroslaw Stanek on 19.01.15.
 */
public class BaseToolBarActivity extends BaseWeatherMenuActivity {

    @Nullable
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.tv_title)
    TextView tvTitle;
    @Nullable
    @Bind(R.id.fl_body)
    ViewGroup flBody;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.base_toobar_activity);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_body), true);
        bindViews();
        setupToolbar();
    }

    protected void bindViews() {
        ButterKnife.bind(this);
    }

    protected void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    public void setContentViewWithoutInject(int layoutResId) {
        super.setContentView(R.layout.base_toobar_activity);
        getLayoutInflater().inflate(layoutResId, (ViewGroup) findViewById(R.id.fl_body), true);
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ButterKnife.unbind(this);
    }

    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
    }

    public void startService(Class<? extends Service> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        startService(intent);
    }

    public void setToolbarTitle(int id) {
        tvTitle.setText(id);
        setNavigationOut();
    }

    public TextView getToolbarTitle() {
        return tvTitle;
    }

    public void setToolbarTitle(String title) {
        tvTitle.setText(title);
        setNavigationOut();
    }

    public void setNavigationOut() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
