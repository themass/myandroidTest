package com.openapi.myapp.data;

import android.content.Context;
import android.content.Intent;

import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.myapp.data.config.IsChainEvent;
import com.openapi.yewu.um.MobAgent;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.bean.vo.UserInfoVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.config.StateUseEvent;
import com.openapi.myapp.data.config.UserLoginEvent;

/**
 * Created by dengt on 2016/8/15.
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
        EventBusUtil.getEventBus().post(new IsChainEvent());
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
