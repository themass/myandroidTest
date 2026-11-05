package com.openapi.ks.myapp.task;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.openapi.core.net.request.CommonResponse;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.core.net.BaseService;
import com.openapi.ks.myapp.data.UserLoginUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by openapi on 2016/8/29.
 */
public class UpdateUserTask {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static String TAG = "update_user";
    BaseService baseService;
    private Context context;

    public UpdateUserTask(Context context) {
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
    }

    public static void start(Context context) {
        new UpdateUserTask(context).execute();
    }

    public void execute() {
        executor.execute(() -> {
            try {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
