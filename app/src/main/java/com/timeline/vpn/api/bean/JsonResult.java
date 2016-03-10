package com.timeline.vpn.api.bean;

public class JsonResult<T>{
    private int errorno = 0;
    private T data;
    private String error;
    private long cost;

    public int getErrorno() {
        return errorno;
    }

    public void setErrorno(int errorno) {
        this.errorno = errorno;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

}
