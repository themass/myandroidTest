package com.timeline.view.bean.vo;

import com.timeline.view.constant.Constants;

import java.io.Serializable;

/**
 * @author gqli
 * @version V1.0
 * @date 2017年2月7日 上午1:43:14
 */
public class TextItemsVo implements Serializable {
    public Integer id;
    public String name;
    public String channel;
    public String fileDate;
    public String fileUrl;

    public FavoriteVo tofavorite() {
        FavoriteVo vo = new FavoriteVo();
        vo.setName(name);
        vo.setO(this);
        vo.setItemUrl(fileUrl);
        vo.setType(Constants.FavoriteType.TEXT);
        return vo;
    }
}

