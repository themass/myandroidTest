package com.timeline.vpn.log;

import android.util.Log;

import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 行为日志记录
 *
 * @author Administrator
 */
public class ActionLog {
    public static String filePath = "";
    private static LinkedBlockingQueue<String> tempQueue = new LinkedBlockingQueue<>();

    public static void addLog(Object content) {
        if (content == null) {
            return;
        }
        try {
            if (content instanceof String) {
                tempQueue.put((String) content);
            } else if (content instanceof Throwable) {
                tempQueue.put(StringUtils.stackTrace2String((Throwable) content));
            }
        } catch (Exception e) {
            LogUtil.error("", e);
        }

    }

    public static String take() {
        try {
            return tempQueue.take();
        } catch (Exception e) {
            LogUtil.error("", e);
        }
        return null;
    }

    public static void recordStringLog(String text) {// 新建或打开日志文件
        File file = new File(FileUtils.getBugFilePath());
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                LogUtil.error("行为日志：在" + filePath + "创建文件失败！", null);
            }
        }
        try {
            FileWriter filerWriter = new FileWriter(file, true);//后面这个参数代表是不是要接上文件中原来的数据，不进行覆盖
            BufferedWriter bufWriter = new BufferedWriter(filerWriter);
            bufWriter.write(text);
            bufWriter.newLine();
            bufWriter.close();
            filerWriter.close();
            Log.d("行为日志写入成功", text);
        } catch (IOException e) {
            LogUtil.error("", e);
        }
    }

}
