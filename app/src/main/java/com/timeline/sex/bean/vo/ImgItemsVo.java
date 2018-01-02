package com.timeline.sex.bean.vo;

import com.timeline.sex.constant.Constants;

/**
 * @author gqli
 * @version V1.0
 * @date 2017年9月3日 上午1:14:45
 */
public class ImgItemsVo {
    public String url;
    public String channel;
    public String name;
    public String fileDate;
    public int pics;
    public String pic;
    public FavoriteVo tofavorite(){
        FavoriteVo vo = new FavoriteVo();
        vo.setItemUrl(url);
        vo.setType(Constants.FavoriteType.IMG);
        vo.setO(this);
        vo.setName(name);
        return vo;
    }
}

