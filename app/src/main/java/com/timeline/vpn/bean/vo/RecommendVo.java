package com.timeline.vpn.bean.vo;

/**
 * @author gqli
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

    @Override
    public String toString() {
        return "RecommendVo{" +
                "title='" + title + '\'' +
                ", actionUrl='" + actionUrl + '\'' +
                ", img='" + img + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}

