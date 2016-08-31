package com.timeline.vpn.task;

import android.content.Context;
import android.os.AsyncTask;

import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;

/**
 * Created by gqli on 2016/8/29.
 */
public class ScoreTask extends AsyncTask {
    private static String TAG="fab_click_tag";
    BaseService  baseService;
    int score;
    private Context context;
    public ScoreTask(Context context,int score){
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
        this.score =score;
    }

    public static void start(Context context,int score){
        new ScoreTask(context,score).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        final UserInfoVo vo = UserLoginUtil.getUserCache();
        if(vo!=null) {
            baseService.getData(String.format(Constants.API_FAB_ADSCLICK_URL,score), new CommonResponse.ResponseOkListener<UserInfoVo>() {
                @Override
                public void onResponse(UserInfoVo o) {
                    super.onResponse(o);
                    o.token = vo.token;
                    UserLoginUtil.login(context,o);
                }
            }, null, TAG, UserInfoVo.class);
        }
        return null;
    }
}
