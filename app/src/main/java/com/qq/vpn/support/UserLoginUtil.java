package com.qq.vpn.support;

import android.content.Context;
import android.content.Intent;

import com.qq.Constants;
import com.qq.MobAgent;
import com.qq.MyApplication;
import com.qq.ext.util.EventBusUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.vpn.domain.res.UserInfoVo;
import com.qq.vpn.support.config.UserLoginEvent;

/**
 * Created by dengt on 2016/8/15.
 */
public class UserLoginUtil {

    public static void login(Context context, UserInfoVo vo) {
        PreferenceUtils.setPrefObj(context, Constants.LOGIN_USER, vo);
        PreferenceUtils.setPrefString(context, Constants.HTTP_TOKEN_KEY, vo.token);
        StaticDataUtil.add(Constants.LOGIN_USER, vo);
        EventBusUtil.getEventBus().post(new UserLoginEvent());
    }

    public static void logout(Context context) {
        PreferenceUtils.remove(context, Constants.LOGIN_USER);
        PreferenceUtils.remove(context, Constants.HTTP_TOKEN_KEY);
        StaticDataUtil.del(Constants.LOGIN_USER);
        EventBusUtil.getEventBus().post(new UserLoginEvent());
    }

    public static UserInfoVo getUserCache() {
        return StaticDataUtil.get(Constants.LOGIN_USER, UserInfoVo.class);
    }
    public static boolean isVIP() {
        UserInfoVo vo = getUserCache();
        return !(vo == null || vo.level < Constants.UserLevel.LEVEL_VIP);
    }

    public static boolean isVIP2() {
        UserInfoVo vo = getUserCache();
        return !(vo == null || vo.level < Constants.UserLevel.LEVEL_VIP2);
    }
    public static boolean isVIP3() {
        UserInfoVo vo = getUserCache();
        return !(vo == null || vo.level < Constants.UserLevel.LEVEL_VIP3);
    }
    public static boolean showAds() {
        UserInfoVo vo = getUserCache();
        return vo == null || !vo.adsNo;
    }
}
