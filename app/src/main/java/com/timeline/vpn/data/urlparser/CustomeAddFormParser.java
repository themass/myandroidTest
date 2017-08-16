package com.timeline.vpn.data.urlparser;

import com.timeline.vpn.bean.form.CustomeAddForm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2017/4/10.
 */

public class CustomeAddFormParser {
    public static List<UrlParser> list = new ArrayList<>();
    static {
        list.add(new HttpParser());
        list.add(new BrowserParser());
    }
    public static CustomeAddForm parser(String url,Integer id,String title){
        for(UrlParser p:list){
            if(p.canParser(url)){
                CustomeAddForm form = p.parser(url);
                form.id = id;
                form.title = title;
                return form;
            }
        }
        return null;
    }
}
