package com.timeline.vpn.bean.vo;

import com.timeline.vpn.constant.Constants;

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
    public FavoriteVo tofavorite(){
        FavoriteVo vo = new FavoriteVo();
        vo.setItemUrl(url);
        vo.setType(Constants.FavoriteType.IMG);
        vo.setO(this);
        vo.setName(name);
        return vo;
    }
}

