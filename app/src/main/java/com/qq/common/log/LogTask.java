package com.qq.common.log;

import android.content.Context;
import android.os.AsyncTask;

import com.qq.common.CommonConstants;
import com.qq.common.util.FileUtils;
import com.qq.common.util.LogUtil;
import com.qq.common.util.StringUtils;

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
            FileUtils.writeLogFile((String) content, CommonConstants.BUG_FILE);
        } else if (content instanceof Throwable) {
            String log = StringUtils.stackTrace2String(((Throwable) content));
            FileUtils.writeLogFile(log, CommonConstants.BUG_FILE);
        }
        return Boolean.TRUE;
    }
}