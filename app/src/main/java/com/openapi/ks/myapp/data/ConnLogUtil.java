package com.openapi.ks.myapp.data;

import android.content.Context;
import android.os.AsyncTask;

import com.openapi.commons.common.util.CollectionUtils;
import com.openapi.commons.common.util.DateUtils;
import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.yewu.net.NetUtils;
import com.openapi.ks.myapp.bean.form.ConnLog;
import com.openapi.ks.myapp.bean.form.ConnLogForm;
import com.openapi.ks.myapp.bean.vo.NullReturnVo;
import com.openapi.ks.myapp.constant.Constants;

import java.util.Date;
import java.util.List;

public class ConnLogUtil {
    public static void addLog(Context context, String name, String host, int status) {
        ConnLog log = new ConnLog();
        log.name = name;
        log.host = host;
        log.status = status;
        String userIp = StaticDataUtil.get(Constants.USERIP,String.class);
        if(StringUtils.hasText(userIp)) {
            log.userIp = userIp;
        }else{
            log.userIp = NetUtils.getIP(context);
        }
        log.time = DateUtils.format(new Date(), DateUtils.DATE_FORMAT);
        new AddInfoTask(context, log).execute();
    }
    public static void sendAllLog(Context context){
        new SendInfoTask(context).execute();
    }

    public static class AddInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private ConnLog log;

        public AddInfoTask(Context context, ConnLog log) {
            this.context = context;
            this.log = log;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                DBManager.getInstance().getDaoSession().getConnLogDao().insert(log);
            }catch (Throwable e){
                LogUtil.e(e);
            }
            return Boolean.TRUE;
        }
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
                List<ConnLog> logs = DBManager.getInstance().getDaoSession().getConnLogDao().loadAll();
                DBManager.getInstance().getDaoSession().getConnLogDao().deleteAll();
                if (!CollectionUtils.isEmpty(logs)) {
                    ConnLogForm form = new ConnLogForm();
                    form.log = GsonUtils.getInstance().toJson(logs);
                    indexService.postData(Constants.getUrl(Constants.API_CONNLOG_URL), form, null, null, "tag", NullReturnVo.class);
                }
            }catch (Throwable e){
                LogUtil.e(e);
            }
            return Boolean.TRUE;
        }
    }
}
