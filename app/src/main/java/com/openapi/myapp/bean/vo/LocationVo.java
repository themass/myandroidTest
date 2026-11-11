package com.openapi.myapp.bean.vo;


import java.io.Serializable;

/**
 * Created by dengt on 2016/8/10.
 */
public class LocationVo implements Serializable{
    public int id;
    public String img;
    public String name;
    public String ename;
    public int type;
    public String cityName;
    public int level;
    public String gateway;
    public int hostId=0;
    public int port;
    public int ttlTime;
    public Integer ping;
    public Object ext;
    public String category;
    /**
     * 节点经纬度，服务端下发（vpn.location_v2.lat/lng）。为 null 时用 GeoCatalog 按城市名兜底。
     * 用 Double 而不是 BigDecimal：服务端下发的是 JSON 数字，Gson 直接映射，
     * 客户端只用于画点，不需要十进制精确性。
     */
    public Double lat;
    public Double lng;
    @Override
    public String toString() {
        return "LocationVo{" +
                "id=" + id +
                ", img='" + img + '\'' +
                ", name='" + name + '\'' +
                ", ename='" + ename + '\'' +
                ", type=" + type +
                ", cityName='" + cityName + '\'' +
                ", level=" + level +
                ", gateway='" + gateway + '\'' +
                ", port=" + port +
                ", ttlTime=" + ttlTime +
                ", ping='" + ping + '\'' +
                ", lat=" + lat +
                ", lng=" + lng +
                '}';
    }
//    public static LocationVo fromNative(NativeAdInfo info){
//        LocationVo vo = new LocationVo();
//        vo.ext =info;
//        vo.img = info.getIconUrl();
//        vo.name = info.getTitle();
//        vo.ename = info.getDesc();
//        return  vo;
//    }
}
