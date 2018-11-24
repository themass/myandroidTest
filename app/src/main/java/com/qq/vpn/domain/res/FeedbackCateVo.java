package com.qq.vpn.domain.res;


import com.qq.vpn.ui.base.fragment.BaseFragment;

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
