package com.timeline.vpn.task;

import android.os.AsyncTask;

import com.sspacee.common.net.HttpUtils;
import com.timeline.vpn.bean.vo.HostVo;

/**
 * Created by themass on 2016/3/8.
 */
public class PingTask extends AsyncTask<HostVo, Void, HostVo> {
    @Override
    protected HostVo doInBackground(HostVo... params) {
        HostVo vo = params[0];
        vo.ttlTime = HttpUtils.ping(vo.gateway);
        return vo;
    }
}
