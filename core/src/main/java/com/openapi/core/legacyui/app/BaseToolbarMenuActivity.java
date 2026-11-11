package com.openapi.core.legacyui.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.widget.SearchView;

import com.openapi.common.util.lang.StringUtils;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Routes;
import com.openapi.ks.myapp.data.UserLoginUtil;

import com.openapi.core.R;
import com.openapi.common.util.log.LogUtil;


/**
 * Created by dengt on 2016/3/1.
 */
public abstract class BaseToolbarMenuActivity extends LogActivity {
    public ImageView ivSetting;
    public SearchView mSearchView;

    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(this, c);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 使用现代Activity过渡动画
            ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 
                R.anim.slide_in_from_right, R.anim.slide_out_to_left);
            startActivity(intent, options.toBundle());
        } else {
            // 兼容旧版本，使用现代方式
            startActivity(intent);
            // 使用现代Activity过渡动画替代overridePendingTransition
            ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 
                R.anim.slide_in_from_right, R.anim.slide_out_to_left);
            // 对于旧版本，直接启动Activity，动画效果由系统处理
        }
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (ivSetting == null) {
            getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        }
        MenuItem setting = menu.findItem(R.id.menu_bar_setting);
//        setting.setActionView(R.layout.common_actionbar_image_view);
//        ivSetting = (ImageView) setting.getActionView().findViewById(R.id.iv_menu);
//        setting.getActionView().setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startActivity(SettingActivity.class);
//            }
//            });
        // 使用正确的SearchView类型
        mSearchView = (SearchView) setting.getActionView();
        mSearchView.setSubmitButtonEnabled(true);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                if (!StringUtils.isEmpty(s)) {
                    RecommendVo vo = new RecommendVo();
                    vo.title = "全局搜索";
                    vo.param = s;
                    startActivity(Routes.globalSearch(BaseToolbarMenuActivity.this, vo));
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return true;
            }
        });
        if (UserLoginUtil.isVIP()) {
            setting.setVisible(true);
            mSearchView.setVisibility(View.VISIBLE);
        } else {
            if (showSearchView()) {
                mSearchView.setVisibility(View.VISIBLE);
            } else {
                mSearchView.setVisibility(View.GONE);
            }
            setting.setVisible(showSearchView());
        }
        return true;
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i("onOptionsMenuClosed");
    }

    public boolean showSearchView() {
        return false;
    }
}
