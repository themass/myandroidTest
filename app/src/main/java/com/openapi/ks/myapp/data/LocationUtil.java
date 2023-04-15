package com.openapi.ks.myapp.data;

import android.content.Context;

import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.vo.LocationVo;
import com.openapi.ks.myapp.constant.Constants;

/**
 * Created by openapi on 2017/1/13.
 */

public class LocationUtil {
    public static String getSelectName(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        String name = vo == null ? context.getString(R.string.location_choose_none) : (SystemUtils.isZH(context) ? vo.name : vo.ename);
        return context.getString(R.string.menu_btn_country) + name;
    }
    public static String getSelectLocationIcon(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        String url = vo == null ? Constants.LOCATION_ICON_ALL : vo.img;
        return url;
    }
    public static String getName(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        if(vo==null){
            return Constants.DEFULT_LOCATION_NAME;
        }
        return vo.name;
    }
    public static int getSelectLocationId(Context context) {
        LocationVo chooseVo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        return chooseVo == null ? 0 : chooseVo.id;
    }
    public static void setLocation(Context context, Object data) {
        PreferenceUtils.setPrefObj(context, Constants.LOCATION_CHOOSE, data);
    }
}
