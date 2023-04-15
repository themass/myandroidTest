package com.openapi.ks.myapp.ui.base.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.SearchView;

import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.ui.fragment.GlobalVideoChannelListFragment;

import com.openapi.ks.moviefree1.R;
import com.qq.e.comm.util.StringUtil;
import com.openapi.commons.common.ui.base.LogActivity;
import com.openapi.commons.common.util.LogUtil;


/**
 * Created by dengt on 2016/3/1.
 */
public abstract class BaseToolbarMenuActivity extends LogActivity {
    public ImageView ivSetting;
    public SearchView mSearchView;
    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(this, c);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
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
        mSearchView = (SearchView) setting.getActionView();
        mSearchView.setSubmitButtonEnabled(true);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                if(!StringUtil.isEmpty(s)) {
                    RecommendVo vo = new RecommendVo();
                    vo.title = "全局搜索";
                    vo.param = s;
                    GlobalVideoChannelListFragment.startFragment(BaseToolbarMenuActivity.this, vo);
                }
                return true;
            }
            @Override
            public boolean onQueryTextChange(String s) {
                return true;
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
