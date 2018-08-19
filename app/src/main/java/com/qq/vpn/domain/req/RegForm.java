package com.qq.vpn.domain.req;

/**
 * Created by dengt on 2016/8/15.
 */
public class RegForm {
    public String name;
    public String pwd;
    public String rePwd;
    public String sex="M";
    public String channel;
    public String code;
    public String email="test@163.com";
    public String ref;

    public RegForm(String name, String pwd, String rePwd, String sex, String email, String ref) {
        this.name = name;
        this.pwd = pwd;
        this.rePwd = rePwd;
        this.sex = "M";
        this.email ="test@163.com";
        this.ref = ref;
    }
}
