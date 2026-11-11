package com.openapi.myapp.service;

import android.content.Intent;

import com.openapi.common.CommonConstants;
import com.openapi.common.util.FileUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.yewu.net.request.CommonResponse;
import com.openapi.myapp.bean.vo.NullReturnVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2016/12/26.
 */
public class LogUploadService extends BaseLogService {
    private static final String TAG = "LOG_UPLOAD";
    /** 用户在「上报问题」弹窗里填的问题描述，可选。 */
    public static final String EXTRA_LABEL = "label";
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            LogUtil.i("删除 日志文件");
            FileUtils.delLogFile(CommonConstants.BUG_FILE_FOR_UPLOAD);
        }
    };
    private BaseService indexService;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String label = intent == null ? null : intent.getStringExtra(EXTRA_LABEL);
        List<File> file = new ArrayList<>();
        // 先把当前环境写进去：只有堆栈没有环境信息，仍然无法判断问题出在哪个网络/协议上
        com.openapi.common.util.DiagnosticLog.snapshot(this);
        boolean ret = FileUtils.mvFile(FileUtils.getBugFilePath(), FileUtils.getBugUploadFilePath());
        boolean ret1 = FileUtils.mvFile(FileUtils.getCharonFilePath(), FileUtils.getCharonUploadFilePath());
        if (ret) {
            File file1 = new File(FileUtils.getBugUploadFilePath());
            if (file1.exists() && file1.length() > 30)
                file.add(file1);
        }
        if (ret1) {
            File file2 = new File(FileUtils.getCharonUploadFilePath());
            if (file2.exists() && file2.length() > 30) {
                file.add(file2);
            }
        }
        if (file.size() > 0) {
            // 带上用户填的描述：只有堆栈和机型，很多时候还是不知道用户实际遇到的问题
            Map<String, String> param = null;
            if (label != null && !label.trim().isEmpty()) {
                param = new HashMap<>();
                param.put("label", label.trim());
            }
            indexService.postData(Constants.getUrl(Constants.API_LOG_URL), file, param,
                    listener, null, Constants.FILE_UPLOAD, TAG, NullReturnVo.class);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        indexService = new BaseService();
        indexService.setup(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        indexService.cancelRequest(TAG);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        indexService.cancelRequest(TAG);
        indexService = null;
    }
}
