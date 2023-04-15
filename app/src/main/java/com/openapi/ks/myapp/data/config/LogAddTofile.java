package com.openapi.ks.myapp.data.config;

import com.openapi.commons.common.log.ActionLog;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by openapi on 2016/3/17.
 */
public class LogAddTofile {

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(LogUtil.LogAddEvent event) {
        LogUtil.i("LogAddTofile onEvent-" + Thread.currentThread().getName());
        String content = null;
        if (event.data == null) {
            return;
        }
        if (event.data instanceof String) {
            content = (String) event.data;
        } else if (event.data instanceof Throwable) {
            content = StringUtils.stackTrace2String((Throwable) event.data);
        }
        if (event.msg != null) {
            content = event.msg + "\r" + content;
        }
        ActionLog.recordStringLog(content);
    }
}
