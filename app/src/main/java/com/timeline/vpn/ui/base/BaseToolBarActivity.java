package com.timeline.vpn.ui.base;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.leakcanary.RefWatcher;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by Miroslaw Stanek on 19.01.15.
 */
public class BaseToolBarActivity extends AppCompatActivity {

    @Nullable
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Nullable
    @Bind(R.id.ivLogo)
    ImageView ivLogo;
    @Bind(R.id.tvTitle)
    TextView tvTitle;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
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
        super.setContentView(layoutResId);
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public ImageView getIvLogo() {
        return ivLogo;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ButterKnife.unbind(this);
        RefWatcher refWatcher = MyApplication.getRefWatcher(this);
        refWatcher.watch(this);
    }

    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        startActivity(intent);
    }

    public void showAlertDialog(String title, String msg, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder adb = new AlertDialog.Builder(BaseToolBarActivity.this);
        adb.setTitle(R.string.alert_text_nocertfound_title);
        adb.setMessage(R.string.alert_text_nocertfound);
        adb.setPositiveButton(android.R.string.ok, listener);
        adb.show();
    }

    public void showAlertDialog(int title, int msg, DialogInterface.OnClickListener listener) {
        showAlertDialog(getString(title), getString(msg), listener);
    }

    public void setToolbarTitle(int id) {
        ivLogo.setVisibility(View.GONE);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(id);
        setNavigationOut();
    }

    public void setToolbarTitle(String title) {
        ivLogo.setVisibility(View.GONE);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(title);
        setNavigationOut();
    }

    public void setNavigationOut() {
        toolbar.setNavigationIcon(R.drawable.left);
        getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

}
