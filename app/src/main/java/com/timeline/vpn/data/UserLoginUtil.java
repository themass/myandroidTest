package com.timeline.vpn.data;

import android.content.Context;
import android.content.Intent;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.config.StateUseEvent;
import com.timeline.vpn.data.config.UserLoginEvent;

/**
 * Created by themass on 2016/8/15.
 */
public class UserLoginUtil {
    public static void initData(Context context) {
        UserInfoVo user = PreferenceUtils.getPrefObj(context, Constants.LOGIN_USER, UserInfoVo.class);
        if (user != null) {
            LogUtil.i("login ok");
            StaticDataUtil.add(Constants.LOGIN_USER, user);
            EventBusUtil.getEventBus().post(new UserLoginEvent());
            context.sendBroadcast(new Intent(MyApplication.UPDATE_STATUS_ACTION));
        }else{
            LogUtil.i("login fail");
        }
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
        if (vo == null || vo.level != Constants.UserLevel.LEVEL_VIP) {
            return false;
        }
        return true;
    }
}
