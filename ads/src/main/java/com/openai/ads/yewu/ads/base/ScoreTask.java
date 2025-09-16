package com.openai.ads.yewu.ads.base;

import android.content.Context;

import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.PreferenceUtils;
import com.openai.net.request.CommonResponse;
import com.openai.net.BaseService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by openapi on 2016/8/29.
 */
public class ScoreTask {
    private static String TAG = "fab_click_tag";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
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
        new ScoreTask(context, score).execute();
    }

    public void execute() {
        executor.execute(() -> {
            // 后台任务逻辑
//            final UserInfoVo vo = UserLoginUtil.getUserCache();
//            if (vo != null) {
//                baseService.getData(String.format(Constants.getUrl(Constants.API_FAB_ADSCLICK_URL), score), new CommonResponse.ResponseOkListener<UserInfoVo>() {
//                    @Override
//                    public void onResponse(UserInfoVo o) {
//                        super.onResponse(o);
//                        o.token = vo.token;
//                        UserLoginUtil.login(context, o);
//                    }
//                }, null, TAG, UserInfoVo.class);
//            } else {
//                int socreTmp = PreferenceUtils.getPrefInt(context, Constants.SCORE_TMP, 0);
//                PreferenceUtils.setPrefInt(context, Constants.SCORE_TMP, score + socreTmp);
//                EventBusUtil.getEventBus().post(new UserLoginEvent());
//            }
        });
    }
}
