package com.timeline.vpn.common.exce;

import com.timeline.vpn.api.bean.JsonResult;

/**
 * Created by gqli on 2016/3/8.
 */
public class JsonResultException extends RuntimeException{
    private JsonResult data;
    public JsonResultException(JsonResult content) {
        this.data = content;
    }
    public JsonResult getErrorData(){
        return data;
    }
}
