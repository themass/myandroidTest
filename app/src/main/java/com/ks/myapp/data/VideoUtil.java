package com.ks.myapp.data;

import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.ks.myapp.constant.Constants;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Created by themass on 2016/4/6.
 */
public class VideoUtil {
    public static String vitamioExt = "avi,rmvb,3gp,wmv";
    public static Set set = new HashSet();
    public static void init(String str) {
        LogUtil.i(str);
        if(StringUtils.hasText(str)){
            set.addAll(Arrays.asList(str.split(",")));
        }else{
            set.addAll(Arrays.asList(vitamioExt.split(",")));
        }
        LogUtil.i(set.toString());
    }

    public static boolean isVitamioExt(String key) {
        try {
            URI uri = new URI(key);
            String path = uri.getPath();
            path = path.substring(path.lastIndexOf(".")+1);
            return set.contains(path.toLowerCase());
        }catch (Exception e){
            return false;
        }
    }
    public static Object[] getVideoSource(String url, boolean loop, String reffer){
        LinkedHashMap map = new LinkedHashMap();
        HashMap<String,String> header = new HashMap<>();
        header.put(Constants.REFERER, reffer);
        header.put(Constants.USER_AGENT, Constants.USER_AGENT_DEF);
        map.put("高清", url);
        Object[] dataSourceObjects = new Object[3];
        dataSourceObjects[0] = map;
        dataSourceObjects[1]=loop;
        dataSourceObjects[2]=header;
        return dataSourceObjects;
    }
}
