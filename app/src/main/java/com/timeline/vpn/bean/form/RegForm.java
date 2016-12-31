package com.timeline.vpn.bean.form;

/**
 * Created by themass on 2016/8/15.
 */
public class RegForm {
    public String name;
    public String pwd;
    public String rePwd;
    public String sex;
    public String channel;
    public String code;

    public RegForm(String name, String pwd, String rePwd, String sex) {
        this.name = name;
        this.pwd = pwd;
        this.rePwd = rePwd;
        this.sex = sex;
    }
}
