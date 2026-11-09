package com.openapi.ks.myapp.base;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.openapi.core.app.AppHooks;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.ui.fragment.GlobalVideoChannelListFragment;
import com.openapi.ks.myapp.ui.user.LoginActivity;
import com.openapi.ks.myapp.ui.user.SettingActivity;

/** :app 注册给 :core 的 AppHooks 实现：把 core 的调用转到各 feature 模块。 */
public final class AppHooksImpl implements AppHooks.Provider {

    @Override
    public boolean isVip() {
        return UserLoginUtil.isVIP();
    }

    @Override
    public void openGlobalSearch(Activity activity, RecommendVo vo) {
        GlobalVideoChannelListFragment.startFragment(activity, vo);
    }

    @Override
    public void openSetting(Context context) {
        context.startActivity(new Intent(context, SettingActivity.class));
    }

    @Override
    public void openLogin(Context context) {
        context.startActivity(new Intent(context, LoginActivity.class));
    }
}
