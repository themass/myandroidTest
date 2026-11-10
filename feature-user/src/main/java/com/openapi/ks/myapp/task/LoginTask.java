package com.openapi.ks.myapp.task;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.openapi.core.net.SyncFuture;
import com.openapi.core.CommonConstants;
import com.openapi.core.util.BeanUtil;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.PreferenceUtils;
import com.openapi.core.util.StringUtils;
import com.openapi.core.net.HttpClient;
import com.openapi.core.net.request.MultipartRequest;
import com.openapi.core.app.AppContext;
import com.openapi.ks.myapp.bean.form.LoginForm;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;

import java.util.Map;


/**
 * Created by openapi on 2016/3/8.
 */
@SuppressWarnings("unchecked")
public class LoginTask {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    public static void start(Context context) {
        new LoginTask().execute();
    }

    public void execute() {
        executor.execute(() -> {
            try {
                SyncFuture<UserInfoVo> future = SyncFuture.newFuture();
                String name = PreferenceUtils.getPrefString(AppContext.get(), Constants.LOGIN_USER_LAST, null);
                if (StringUtils.isEmpty(name)) {
                    return;
                }
                String pwd = PreferenceUtils.getPrefString(AppContext.get(), Constants.LOGIN_USER_PW_LAST, null);
                if (StringUtils.isEmpty(pwd)) {
                    return;
                }
                int score = PreferenceUtils.getPrefInt(AppContext.get(), Constants.SCORE_TMP, 0);
                LoginForm form = new LoginForm(name, pwd, score);
                Map<String, String> map = BeanUtil.transBean2Map(form);
                MultipartRequest request = new MultipartRequest(AppContext.get(), map, Constants.getUrl(Constants.API_LOGIN_URL), null, future, future, UserInfoVo.class);
                request.setTag("login");
                HttpClient.addRequest(request);
                UserInfoVo user = future.get();
                if (user != null) {
                    UserLoginUtil.login(AppContext.get(), user);
                } else {
                    PreferenceUtils.remove(AppContext.get(), com.openapi.core.net.NetConfig.tokenHeader());
                }
            } catch (Exception e) {
                LogUtil.e(e);
            }
        });
    }
}
