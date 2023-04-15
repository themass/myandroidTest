package com.openapi.myapp.task;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import com.openapi.common.util.DeviceInfoUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.bean.vo.NullReturnVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.net.VolleyUtils;
import com.openapi.yewu.net.request.MultipartRequest;

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
                    +Build.DEVICE+";PRODUCT="+Build.PRODUCT+
                    ";xposedExistByThrow="+ DeviceInfoUtils.xposedExistByThrow();
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
