package com.timeline.vpn.ads;

import com.timeline.vpn.common.util.LogUtil;

/**
 * Created by gqli on 2016/3/23.
 */
public class AdsStrategy {
    private int index=0;
    private String[] mode;
    public AdsStrategy(String[] list,String[] defaultList){
        LogUtil.i("ops------------------");
        if(list!=null&&list.length>0){
            this.mode=list;
        }else{
            this.mode = defaultList;
        }

    }
    public String getNext(int postion){
        return mode[postion%mode.length];
    }
    public String getNext(){
        if(index>=mode.length){
            return null;
        }
        return mode[index++];
    }
    public void init(){
        index=0;
    }
    public int size(){
        return mode.length;
    }

}
