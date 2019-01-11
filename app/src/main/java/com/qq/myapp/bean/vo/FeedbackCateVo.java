package com.qq.myapp.bean.vo;

import android.app.Fragment;

import com.sspacee.common.ui.base.BaseFragment;

/**
 * Created by themass on 2016/9/5.
 */
public class FeedbackCateVo {
    public String name;
    public Class<? extends BaseFragment> clasz;
    public FeedbackCateVo(String name, Class<? extends BaseFragment>  clasz){
        this.name=name;
        this.clasz = clasz;
    }
}
