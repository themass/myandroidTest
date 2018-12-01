package com.qq.myapp.bean.vo;

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
                '}';
    }
}
