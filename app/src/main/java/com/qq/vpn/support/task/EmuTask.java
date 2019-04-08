package com.qq.vpn.support.task;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import com.android.volley.toolbox.RequestFuture;
import com.lahm.library.EasyProtectorLib;
import com.qq.Constants;
import com.qq.MyApplication;
import com.qq.ext.network.VolleyUtils;
import com.qq.ext.network.req.MultipartRequest;
import com.qq.ext.util.BeanUtil;
import com.qq.ext.util.DeviceInfoUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.StringUtils;
import com.qq.vpn.domain.req.LoginForm;
import com.qq.vpn.domain.res.NullReturnVo;
import com.qq.vpn.domain.res.UserInfoVo;
import com.qq.vpn.support.UserLoginUtil;

import java.util.HashMap;
import java.util.Map;


/**
 * Created by dengt on 2016/3/8.
 */
public class EmuTask extends AsyncTask<Void, Void, Void> {
    public static void start(Context context) {
        new EmuTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    @Override
    protected Void doInBackground(Void... params) {
        try {
            Map<String, String> map =  new HashMap<>();
            String conent = "FINGERPRINT="+ Build.FINGERPRINT+";MODEL="+Build.MODEL+";SERIAL"+Build.SERIAL+
                    ";MANUFACTURER="+Build.MANUFACTURER+";BRAND="+Build.BRAND+";DEVICE="
                    +Build.DEVICE+";PRODUCT="+Build.PRODUCT+";checkIsRunningInEmulator="+ EasyProtectorLib.checkIsRunningInEmulator(MyApplication.getInstance(),null)+
                    ";checkIsRunningInEmulator="+ DeviceInfoUtils.xposedExistByThrow();
            map.put("dev",conent);
            MultipartRequest request = new MultipartRequest(MyApplication.getInstance(), map, Constants.getUrl(Constants.API_EMU), null, null, null, NullReturnVo.class);
            request.setTag("emu");
            VolleyUtils.addRequest(request);
        }catch (Exception e){
            LogUtil.e(e);
        }
        return null;
    }
}
