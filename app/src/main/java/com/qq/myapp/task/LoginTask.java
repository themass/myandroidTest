package com.qq.myapp.task;

import android.content.Context;
import android.os.AsyncTask;

import com.android.volley.toolbox.RequestFuture;
import com.qq.e.comm.util.StringUtil;
import com.qq.common.util.BeanUtil;
import com.qq.common.util.LogUtil;
import com.qq.common.util.PreferenceUtils;
import com.qq.yewu.net.VolleyUtils;
import com.qq.yewu.net.request.MultipartRequest;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.bean.form.LoginForm;
import com.qq.myapp.bean.vo.UserInfoVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.UserLoginUtil;

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
            if (StringUtil.isEmpty(name)) {
                return null;
            }
            String pwd = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.LOGIN_USER_PW_LAST, null);
            if ( StringUtil.isEmpty(pwd)) {
                return null;
            }
            int score = PreferenceUtils.getPrefInt(MyApplication.getInstance(), Constants.SCORE_TMP, 0);
            LoginForm form = new LoginForm(name, pwd, score);
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
