package com.qq.vpn.support.task;

import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.qq.ext.network.HttpUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.StringUtils;
import com.qq.network.R;
import com.qq.vpn.domain.res.LocationVo;


/**
 * Created by dengt on 2016/3/8.
 */
public class LocationPingTask extends AsyncTask<Void, Void, Void> {
    Context context;
    LocationVo vo;
    ProgressBar bar;
    TextView tvPing;
    public static void ping(Context context, LocationVo vo, ProgressBar bar, TextView tvPing){
        bar.setVisibility(View.VISIBLE);
        tvPing.setVisibility(View.GONE);
        if(StringUtils.hasText(vo.gateway)) {
            bar.setTag(vo.gateway);
        }else {
            vo.ping=-1;
        }
        if (vo.ping!=null) {
            fillText(context,bar,tvPing,vo.ping);
        } else {
            try {
                new LocationPingTask(context, vo, bar, tvPing).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }catch (RuntimeException e){
                LogUtil.e(e);
            }
        }
    }
    public static void fillText(Context context,ProgressBar bar, TextView tvPing,Integer ping){
        bar.setVisibility(View.GONE);
        tvPing.setVisibility(View.VISIBLE);
        if(ping==null || -1 ==ping){
            tvPing.setTextColor(context.getResources().getColor(R.color.base_black));
            tvPing.setText(" no ping");
            return;
        }
        if(ping>130 && ping<200){
            ping = ping -15;
        }else if(ping>=200 && ping <400){
            ping = ping -20;
        }else if(ping>=400){
            ping = ping-30;
        }
        if(0 ==ping){
            tvPing.setTextColor(context.getResources().getColor(R.color.base_red));
        }else if(ping<=200){
            tvPing.setTextColor(context.getResources().getColor(R.color.base_green1));
        }else if(ping>200 && ping<=400 ){
            tvPing.setTextColor(context.getResources().getColor(R.color.warning));
        }else{
            tvPing.setTextColor(context.getResources().getColor(R.color.base_gray));
        }
        tvPing.setText(ping +" ms");
    }
    public LocationPingTask(Context context, LocationVo vo, ProgressBar bar, TextView tvPing){
        this.context = context;
        this.vo = vo;
        this.bar = bar;
        this.tvPing = tvPing;
    }
    @Override
    protected Void doInBackground(Void... params) {
        try {
            vo.ping = HttpUtils.pingVal(vo.gateway);
            LogUtil.i(vo.toString());
        }catch (Exception e){
            LogUtil.e(e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if(this.bar!=null && this.bar.getTag().equals(vo.gateway)){
            fillText(context,bar,tvPing,vo.ping);
        }
    }

}