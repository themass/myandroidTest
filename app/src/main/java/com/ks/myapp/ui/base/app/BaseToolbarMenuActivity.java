package com.ks.myapp.ui.base.app;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.ks.myapp.ui.user.SettingActivity;
import com.ks.sexfree1.R;
import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.LogUtil;


/**
 * Created by dengt on 2016/3/1.
 */
public abstract class BaseToolbarMenuActivity extends LogActivity {
    public ImageView ivSetting;

    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(this, c);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (ivSetting == null) {
            getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        }
        MenuItem setting = menu.findItem(R.id.menu_bar_setting);
        setting.setActionView(R.layout.common_actionbar_image_view);
        ivSetting = (ImageView) setting.getActionView().findViewById(R.id.iv_menu);
        setting.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(SettingActivity.class);
            }
            });
        return true;
    }
    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i("onOptionsMenuClosed");
    }
}
