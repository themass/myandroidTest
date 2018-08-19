package com.qq.vpn.support;

import android.content.Context;
import android.os.AsyncTask;

import com.qq.ext.network.NetUtils;
import com.qq.ext.util.CollectionUtils;
import com.qq.ext.util.DateUtils;
import com.qq.ext.util.GsonUtils;
import com.qq.ext.util.LogUtil;
import com.qq.vpn.domain.req.ConnLog;
import com.qq.vpn.domain.req.ConnLogForm;
import com.qq.vpn.domain.res.NullReturnVo;
import com.qq.Constants;

import java.util.Date;
import java.util.List;

public class ConnLogReport {
    public static void addLog(Context context, String name, String host, int status) {
        ConnLog log = new ConnLog();
        log.name = name;
        log.host = host;
        log.status = status;
        log.userIp = NetUtils.getIP(context);
        log.time = DateUtils.format(new Date(), DateUtils.DATE_FORMAT);
        new AddInfoTask(context, log).execute();
    }
    public static void send(Context context){
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
                com.qq.vpn.support.DBManager.getInstance().getDaoSession().getConnLogDao().insert(log);
            }catch (Throwable e){
                LogUtil.e(e);
            }
            return Boolean.TRUE;
        }
    }
    public static class SendInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        protected com.qq.vpn.support.NetApiUtil api;
        public SendInfoTask(Context context) {
            api = new com.qq.vpn.support.NetApiUtil(context);
            this.context = context;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                List<ConnLog> logs = com.qq.vpn.support.DBManager.getInstance().getDaoSession().getConnLogDao().loadAll();
                com.qq.vpn.support.DBManager.getInstance().getDaoSession().getConnLogDao().deleteAll();
                if (!CollectionUtils.isEmpty(logs)) {
                    ConnLogForm form = new ConnLogForm();
                    form.log = GsonUtils.getInstance().toJson(logs);
                    api.postData(Constants.getUrl(Constants.API_CONNLOG_URL), form, null, null, "tag", NullReturnVo.class);
                }
            }catch (Throwable e){
                LogUtil.e(e);
            }
            return Boolean.TRUE;
        }
    }
}
