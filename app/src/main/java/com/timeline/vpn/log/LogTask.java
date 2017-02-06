package com.timeline.vpn.log;

import android.content.Context;
import android.os.AsyncTask;

import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.timeline.vpn.constant.Constants;

import java.io.Serializable;

public class LogTask extends AsyncTask<String, Integer, Boolean> {
    private Serializable content;
    private Context context;

    public LogTask(Context context, Serializable content) {
        this.content = content;
        this.context = context;
    }

    @Override
    protected Boolean doInBackground(String... params) {
        LogUtil.i("write log back");
        if (content instanceof String) {
            FileUtils.writeLogFile((String) content, Constants.BUG_FILE);
        } else if (content instanceof Throwable) {
            String log = StringUtils.stackTrace2String(((Throwable) content));
            FileUtils.writeLogFile(log, Constants.BUG_FILE);
        }
        return Boolean.TRUE;
    }
}