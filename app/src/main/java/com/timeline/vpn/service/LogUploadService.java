package com.timeline.vpn.service;

import android.content.Intent;

import com.sspacee.common.CommonConstants;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2016/12/26.
 */
public class LogUploadService extends BaseLogService {
    private static final String TAG = "LOG_UPLOAD";
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
        List<File> file = new ArrayList<>();
        boolean ret = FileUtils.mvFile(FileUtils.getBugFilePath(), FileUtils.getBugUploadFilePath());
        boolean ret1 = FileUtils.mvFile(FileUtils.getCharonFilePath(), FileUtils.getCharonUploadFilePath());
        if (ret) {
            File file1 = new File(FileUtils.getBugUploadFilePath());
            if (file1.exists() && file1.length() > 30)
                file.add(file1);
        }
        if (ret1) {
            File file2 = new File(FileUtils.getCharonUploadFilePath());
            if (file2.exists() && file2.length() > 30)
                file.add(file2);
            file.add(file2);
        }
        if (file.size() > 0)
            indexService.postData(Constants.getUrl(Constants.API_LOG_URL), file, listener, null, Constants.FILE_UPLOAD, TAG, NullReturnVo.class);
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
