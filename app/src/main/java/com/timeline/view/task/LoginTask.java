package com.timeline.view.task;

import android.content.Context;
import android.os.AsyncTask;

import com.android.volley.toolbox.RequestFuture;
import com.qq.e.comm.util.StringUtil;
import com.sspacee.common.util.BeanUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.net.VolleyUtils;
import com.sspacee.yewu.net.request.MultipartRequest;
import com.timeline.view.base.MyApplication;
import com.timeline.view.bean.form.LoginForm;
import com.timeline.view.bean.vo.UserInfoVo;
import com.timeline.view.constant.Constants;
import com.timeline.view.data.UserLoginUtil;

import java.util.Map;


/**
 * Created by themass on 2016/3/8.
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
