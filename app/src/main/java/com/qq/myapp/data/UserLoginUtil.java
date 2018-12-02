package com.qq.myapp.data;

import android.content.Context;
import android.content.Intent;

import com.qq.common.util.EventBusUtil;
import com.qq.common.util.PreferenceUtils;
import com.qq.yewu.um.MobAgent;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.bean.vo.UserInfoVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.config.StateUseEvent;
import com.qq.myapp.data.config.UserLoginEvent;

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
