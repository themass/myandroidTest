package com.timeline.vpn.ui.task;

import android.os.AsyncTask;

import com.timeline.vpn.bean.vo.HostVo;
import com.timeline.vpn.common.net.HttpUtils;

/**
 * Created by gqli on 2016/3/8.
 */
public class PingTask extends AsyncTask<HostVo, Void, HostVo>
{
    @Override
    protected HostVo doInBackground(HostVo... params)
    {
        HostVo vo = params[0];
        int ttl = HttpUtils.ping(vo.gateway);
        vo.ttlTime = ttl;
        return vo;
    }
}
