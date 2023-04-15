package com.openapi.ks.myapp.data;

import android.content.Context;
import android.content.Intent;

import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.config.StateUseEvent;
import com.openapi.ks.myapp.data.config.UserLoginEvent;

/**
 * Created by openapi on 2016/8/15.
 */
public class UserLoginUtil {
    public static UserInfoVo getStoreData(Context context) {
        return PreferenceUtils.getPrefObj(context, Constants.LOGIN_USER, UserInfoVo.class);

//        if (user != null) {
//            LogUtil.i("login ok");
//            StaticDataUtil.add(Constants.LOGIN_USER, user);
//            EventBusUtil.getEventBus().post(new UserLoginEvent());
//            context.sendBroadcast(new Intent(MyApplication.UPDATE_STATUS_ACTION));
//        } else {
//            LogUtil.i("login fail");
//        }
    }

    public static void login(Context context, UserInfoVo vo) {
        PreferenceUtils.setPrefObj(context, Constants.LOGIN_USER, vo);
        PreferenceUtils.setPrefString(context, Constants.HTTP_TOKEN_KEY, vo.token);
        StaticDataUtil.add(Constants.LOGIN_USER, vo);
        EventBusUtil.getEventBus().post(new UserLoginEvent());
        context.sendBroadcast(new Intent(MyApplication.UPDATE_STATUS_ACTION));
        if (vo.stateUse != null)
            EventBusUtil.getEventBus().post(new StateUseEvent(vo.stateUse));
        MobAgent.onProfileSignIn(vo.name);
    }

    public static void logout(Context context) {
        PreferenceUtils.remove(context, Constants.LOGIN_USER);
        PreferenceUtils.remove(context, Constants.HTTP_TOKEN_KEY);
        StaticDataUtil.del(Constants.LOGIN_USER);
        EventBusUtil.getEventBus().post(new UserLoginEvent());
        context.sendBroadcast(new Intent(MyApplication.UPDATE_STATUS_ACTION));
        MobAgent.onProfileSignOff();
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
