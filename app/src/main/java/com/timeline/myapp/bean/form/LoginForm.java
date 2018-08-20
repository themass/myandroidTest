package com.timeline.myapp.bean.form;

/**
 * Created by themass on 2016/8/14.
 */
public class LoginForm {
    public String name;
    public String pwd;
    public String score;

    public LoginForm(String name, String pwd, Integer score) {
        this.name = name;
        this.pwd = pwd;
        if (score != null) {
            this.score = String.valueOf(score);
        }
    }
}