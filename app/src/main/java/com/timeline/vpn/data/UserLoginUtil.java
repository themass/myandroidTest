package com.timeline.vpn.data;

import android.content.Context;

import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.config.UserLoginEvent;

/**
 * Created by gqli on 2016/8/15.
 */
public class UserLoginUtil {
    public static void login(Context context, UserInfoVo vo){
        PreferenceUtils.setPrefObj(context, Constants.LOGIN_USER, vo);
        PreferenceUtils.setPrefString(context,Constants.HTTP_TOKEN_KEY,vo.token);
        StaticDataUtil.add(Constants.LOGIN_USER, vo);
        EventBusUtil.getEventBus().post(new UserLoginEvent());
        MobAgent.onProfileSignIn(vo.name);
    }
    public static void logout(Context context){
        PreferenceUtils.remove(context, Constants.LOGIN_USER);
        PreferenceUtils.remove(context, Constants.HTTP_TOKEN_KEY);
        StaticDataUtil.del(Constants.LOGIN_USER);
        EventBusUtil.getEventBus().post(new UserLoginEvent());
        MobAgent.onProfileSignOff();
    }
}
