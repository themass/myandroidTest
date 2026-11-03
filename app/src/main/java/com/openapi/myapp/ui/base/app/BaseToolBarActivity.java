package com.openapi.myapp.ui.base.app;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.openapi.common.util.LogUtil;
import com.openapi.common.ui.base.EdgeToEdgeHelper;
import com.openapi.ks.free1.R;

import java.lang.reflect.Field;

/**
 * Created by Miroslaw Stanek on 19.01.15.
 */
public abstract class BaseToolBarActivity extends BaseWeatherMenuActivity {

    @Nullable
    Toolbar toolbar;
    TextView tvTitle;
    @Nullable
    ViewGroup flBody;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.base_toobar_activity);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_body), true);
        bindViews();
        setupToolbar();
        EdgeToEdgeHelper.applyToolbarLayout(findViewById(R.id.toolbar), flBody);
    }

    public void setupView() {

    }

    protected void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTitle = findViewById(R.id.tv_title);
        flBody = findViewById(R.id.fl_body);
        setupView();
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

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LogUtil.i("onConfigurationChanged->" + newConfig.orientation);
    }
    public View getNaviButton() {
        Field field = null;
        try {
            field =Toolbar.class.getDeclaredField("mNavButtonView");
            field.setAccessible(true);
            return  (View) field.get(getToolbar());
        } catch (NoSuchFieldException e) {
            LogUtil.e(e);
        } catch (IllegalAccessException e) {
            LogUtil.e(e);
        }
        return null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
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

    public void stopService(Class<? extends Service> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        stopService(intent);
    }

    public void showToolbar(boolean show) {
        if (show) {
            toolbar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    public void setToolbarTitle(int id, boolean initNav) {
        tvTitle.setText(id);
        if (initNav)
            setNavigationOut();
    }

    public void setToolbarTitle(String title, boolean initNav) {
        tvTitle.setText(title);
        if (initNav)
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
