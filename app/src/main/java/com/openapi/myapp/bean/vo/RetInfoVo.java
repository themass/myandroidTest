package com.openapi.myapp.bean.vo;

/**
 * 后端 {@code RetInfoVo} 的客户端镜像：服务端用 {@code {"ret": true}} 表示"操作成功，
 * 但这次调用没有业务数据要返回"（见后端 {@code Constant.RESULT_SUCCESS}）。
 * <p>
 * 只用于承接这类"无需读取返回值"的接口的 {@code data} 字段。是否成功由统一的
 * {@code errno} 判定完成（{@code BaseRequest.parserData()}），与本类无关。
 */
public class RetInfoVo {
    public Object ret;
}
