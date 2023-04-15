package com.openapi.myapp.bean.vo;

import com.openapi.common.ui.base.BaseFragment;

/**
 * Created by dengt on 2016/9/5.
 */
public class FeedbackCateVo {
    public String name;
    public Class<? extends BaseFragment> clasz;
    public FeedbackCateVo(String name, Class<? extends BaseFragment>  clasz){
        this.name=name;
        this.clasz = clasz;
    }
}
