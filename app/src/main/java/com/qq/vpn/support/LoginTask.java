package com.qq.vpn.support;

import android.content.Context;
import android.os.AsyncTask;

import com.android.volley.toolbox.RequestFuture;
import com.qq.Constants;
import com.qq.MyApplication;
import com.qq.ext.network.VolleyUtils;
import com.qq.ext.network.req.MultipartRequest;
import com.qq.ext.util.BeanUtil;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.StringUtils;
import com.qq.vpn.domain.req.LoginForm;
import com.qq.vpn.domain.res.UserInfoVo;

import java.util.Map;


/**
 * Created by dengt on 2016/3/8.
 */
public class LoginTask extends AsyncTask<Void, Void, Void> {
    public static void start(Context context) {
        new LoginTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    @Override
    protected Void doInBackground(Void... params) {
        try {
            RequestFuture<UserInfoVo> future = RequestFuture.newFuture();
            String name = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.LOGIN_USER_LAST, null);
            if (!StringUtils.hasText(name)) {
                return null;
            }
            String pwd = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.LOGIN_USER_PW_LAST, null);
            if ( !StringUtils.hasText(pwd)) {
                return null;
            }
            LoginForm form = new LoginForm(name, pwd, 0);
            Map<String, String> map =  BeanUtil.transBean2Map(form);
            MultipartRequest request = new MultipartRequest(MyApplication.getInstance(), map, Constants.getUrl(Constants.API_LOGIN_URL), null, future, future, UserInfoVo.class);
            request.setTag("login");
            VolleyUtils.addRequest(request);
            UserInfoVo user = future.get();
            if(user!=null){
                UserLoginUtil.login(MyApplication.getInstance(),user);
            }
        }catch (Exception e){
            LogUtil.e(e);
        }
        return null;
    }
}
