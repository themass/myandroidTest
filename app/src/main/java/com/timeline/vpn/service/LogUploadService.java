package com.timeline.vpn.service;

import android.content.Intent;

import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2016/12/26.
 */
public class LogUploadService extends BaseLogService {
    private static final String TAG="LOG_UPLOAD";
    private BaseService indexService;
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            LogUtil.i("删除 日志文件");
            FileUtils.delLogFile(Constants.BUG_FILE_FOR_UPLOAD);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        List<File> file = new ArrayList<>();
        boolean ret = FileUtils.mvFile(FileUtils.getBugFilePath(),FileUtils.getBugUploadFilePath());
        boolean ret1 = FileUtils.mvFile(FileUtils.getCharonFilePath(),FileUtils.getCharonUploadFilePath());
        if(ret)
            file.add(new File( FileUtils.getBugUploadFilePath()));
        if(ret1)
            file.add(new File(FileUtils.getCharonUploadFilePath()));
        indexService.postData(Constants.getUrl(Constants.API_LOG_URL),file,listener,null, Constants.FILE_UPLOAD,TAG,NullReturnVo.class);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        indexService = new BaseService();
        indexService.setup(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        indexService.cancelRequest(TAG);
        indexService = null;
    }
}