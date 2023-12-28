package com.openapi.commons.common.util.netglide;

import android.content.Context;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.net.HttpDNSUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;

public class OkHttpDns implements Dns {
    private static final Dns SYSTEM = Dns.SYSTEM;
    private static Map<String,List<InetAddress>> cache = new HashMap<>();
    private static OkHttpDns instance = null;
    private OkHttpDns(Context context) {
    }
    public static OkHttpDns getInstance(Context context) {
        if(instance == null) {
            instance = new OkHttpDns(context);
        }
        return instance;
    }
    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        //通过异步解析接口获取ip
        if(cache.get(hostname)!=null){
            LogUtil.i("OkHttpDns from cache");
            return cache.get(hostname);
        }
        List<InetAddress> inetAddresses =  Dns.SYSTEM.lookup(hostname);
        LogUtil.i("OkHttpDns  inetAddresses:" + inetAddresses);
        if(inetAddresses.size()>1){
            cache.put(hostname,inetAddresses);
            return inetAddresses;
        }else if(inetAddresses.size()==1 && !inetAddresses.get(0).getHostAddress().equals("0.0.0.0")){
            cache.put(hostname,inetAddresses);
            return inetAddresses;
        }

        String ip = HttpDNSUtil.getIPByHostNotCache(hostname);
            //如果ip不为null，直接使用该ip进行网络请求
        inetAddresses = Arrays.asList(InetAddress.getAllByName(ip));
        LogUtil.i("OkHttpDns mydns inetAddresses:" + inetAddresses);
        cache.put(hostname,inetAddresses);
        return inetAddresses;

    }
}