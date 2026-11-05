package com.openapi.ks.myapp.data;

import android.content.Context;
import android.os.AsyncTask;

import com.openapi.core.util.CollectionUtils;
import com.openapi.core.util.StaticDataUtil;
import com.openapi.core.net.BaseService;
import com.openapi.core.util.DateUtils;
import com.openapi.core.util.GsonUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.StringUtils;
import com.openapi.core.net.NetUtils;
import com.openapi.ks.myapp.bean.form.ConnLog;
import com.openapi.ks.myapp.bean.form.ConnLogForm;
import com.openapi.ks.myapp.bean.vo.NullReturnVo;
import com.openapi.ks.myapp.constant.Constants;

import java.util.Date;
import java.util.List;

public class ConnLogUtil {

    public static void sendAllLog(Context context) {
        new SendInfoTask(context).execute();
    }


    public static class SendInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        protected BaseService indexService;

        public SendInfoTask(Context context) {
            indexService = new BaseService();
            indexService.setup(context);
            this.context = context;
        }
        @Override
        protected Boolean doInBackground(String... params) {
            try {
                List<ConnLog> logs = DBManager.getInstance().connLogDao().loadAll();
                DBManager.getInstance().connLogDao().deleteAll();
                if (!CollectionUtils.isEmpty(logs)) {
                    ConnLogForm form = new ConnLogForm();
                    form.log = GsonUtils.getInstance().toJson(logs);
                    indexService.postData(Constants.getUrl(Constants.API_CONNLOG_URL), form, null, null, "tag", NullReturnVo.class);
                }
            } catch (Throwable e) {
                LogUtil.e(e);
            }
            return Boolean.TRUE;
        }
    }
}
