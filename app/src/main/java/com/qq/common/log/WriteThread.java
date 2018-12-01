package com.qq.common.log;

import com.qq.common.util.LogUtil;

public class WriteThread extends Thread {
    public volatile boolean isRun = true;

    public WriteThread() {
    }

    @Override
    public void run() {
        LogUtil.i("logThread start");
        while (isRun) {
            try {
                String log = ActionLog.take();
                if (log == null) {
                    isRun = false;
                    break;
                }
                ActionLog.recordStringLog(log);
            } catch (Exception e) {
                LogUtil.error("", e);
            }
        }
        LogUtil.i("logThread stop");
    }

    @Override
    public synchronized void start() {
        isRun = true;
        super.start();
    }

    public void stopRun() {
        isRun = false;
    }
}