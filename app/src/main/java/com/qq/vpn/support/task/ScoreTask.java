package com.qq.vpn.support.task;

import android.content.Context;
import android.os.AsyncTask;

import com.qq.Constants;
import com.qq.ext.network.req.CommonResponse;
import com.qq.ext.util.EventBusUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.vpn.domain.res.UserInfoVo;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.support.UserLoginUtil;
import com.qq.vpn.support.config.UserLoginEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dengt on 2016/8/29.
 */
public class ScoreTask extends AsyncTask {
    private static String TAG = "fab_click_tag";
    NetApiUtil api;
    int score;
    private Context context;

    public ScoreTask(Context context, int score) {
        this.context = context;
        api = new NetApiUtil(context);
        this.score = score;
    }

    public static void start(Context context, int score) {
        new ScoreTask(context, score).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        final UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            Map<String,String> header = new HashMap<>();
            header.put("CookieCache",score+"sdsktesstkey");
            api.postData(Constants.getUrl(Constants.API_FAB_ADSCLICK2_URL),null,header, new CommonResponse.ResponseOkListener<UserInfoVo>() {
                @Override
                public void onResponse(UserInfoVo o) {
                    super.onResponse(o);
                    o.token = vo.token;
                    UserLoginUtil.login(context, o);
                }
            }, null, TAG, UserInfoVo.class);
        } else {
            int socreTmp = PreferenceUtils.getPrefInt(context, Constants.SCORE_TMP, 0);
            PreferenceUtils.setPrefInt(context, Constants.SCORE_TMP, score + socreTmp);
            EventBusUtil.getEventBus().post(new UserLoginEvent());
        }
        return null;
    }
}
