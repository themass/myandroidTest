package com.qq.vpn.domain.res;

import java.io.Serializable;

/**
 * @author dengt
 * @version V1.0
 * @date 2016年3月10日 下午4:41:23
 */
public class RecommendVo implements Serializable {
    public static final int dataType_ADS = 1;
    public static final int dataType_RECOMMENT = 2;
    public static final int dataType_TEXT_CHANNEL = 3;
    public static final int dataType_SOUND_CHANNEL = 4;
    public static final int dataType_VIDEO_CHANNEL = 5;
    public static final int dataType_IMG_CHANNEL = 6;
    public String title;
    public String actionUrl;
    public String img;
    public String desc;
    public int imgType;
    public float rate = 1; //长/宽
    public String color;
    public boolean adsShow;
    public boolean adsPopShow;
    public int type;
    public int showType;
    public Integer id;
    public String param;
    public Object extra;
    public int dataType = dataType_RECOMMENT;
    public String minVersion;
    public Boolean newShow;
    public String showLogo;
}

