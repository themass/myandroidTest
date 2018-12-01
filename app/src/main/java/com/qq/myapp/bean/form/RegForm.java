package com.qq.myapp.bean.form;

/**
 * Created by dengt on 2016/8/15.
 */
public class RegForm {
    public String name;
    public String pwd;
    public String rePwd;
    public String sex;
    public String channel;
    public String code;
    public String email;
    public String ref;

    public RegForm(String name, String pwd, String rePwd, String sex, String email, String ref) {
        this.name = name;
        this.pwd = pwd;
        this.rePwd = rePwd;
        this.sex = sex;
        this.email = email;
        this.ref = ref;
    }
}
