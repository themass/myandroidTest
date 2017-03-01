package com.timeline.vpn.task;

import android.content.Context;
import android.os.AsyncTask;

import com.sspacee.common.net.request.CommonResponse;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;

/**
 * Created by themass on 2016/8/29.
 */
public class UpdateUserTask extends AsyncTask {
    private static String TAG = "update_user";
    BaseService baseService;
    private Context context;

    public UpdateUserTask(Context context) {
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
    }

    public static void start(Context context) {
        new UpdateUserTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        final UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            baseService.getData(Constants.getUrl(Constants.API_USER_INFO_URL), new CommonResponse.ResponseOkListener<UserInfoVo>() {
                @Override
                public void onResponse(UserInfoVo o) {
                    super.onResponse(o);
                    if (o != null) {
                        UserLoginUtil.login(context, o);
                    }
                }
            }, null, TAG, UserInfoVo.class);
        }
        return null;
    }
}
