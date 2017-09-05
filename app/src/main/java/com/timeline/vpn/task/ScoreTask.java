package com.timeline.vpn.task;

import android.content.Context;
import android.os.AsyncTask;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.UserLoginEvent;

/**
 * Created by themass on 2016/8/29.
 */
public class ScoreTask extends AsyncTask {
    private static String TAG = "fab_click_tag";
    BaseService baseService;
    int score;
    private Context context;

    public ScoreTask(Context context, int score) {
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
        this.score = score;
    }

    public static void start(Context context, int score) {
        new ScoreTask(context, score).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        final UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            baseService.getData(String.format(Constants.getUrl(Constants.API_FAB_ADSCLICK_URL), score), new CommonResponse.ResponseOkListener<UserInfoVo>() {
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
