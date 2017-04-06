package com.timeline.vpn.bean.vo;

/**
 * @author themass
 * @version V1.0
 * @date 2016年3月10日 下午4:41:23
 */
public class RecommendVo {
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

    @Override
    public String toString() {
        return "RecommendVo{" +
                "title='" + title + '\'' +
                ", actionUrl='" + actionUrl + '\'' +
                ", img='" + img + '\'' +
                ", desc='" + desc + '\'' +
                ", imgType=" + imgType +
                ", rate=" + rate +
                ", color='" + color + '\'' +
                ", adsShow=" + adsShow +
                ", adsPopShow=" + adsPopShow +
                ", type=" + type +
                ", showType=" + showType +
                ", id=" + id +
                '}';
    }
}

