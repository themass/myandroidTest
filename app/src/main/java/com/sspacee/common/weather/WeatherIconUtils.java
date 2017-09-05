package com.sspacee.common.weather;

import com.sspacee.common.CommonConstants;
import com.timeline.vpn.R;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WeatherIconUtils {

    private WeatherIconUtils() {
    }

    /**
     * 获取天气图标
     *
     * @param type
     * @return
     */
    public static int getWeatherIcon(int type) {
        // 如果是晚上
        if (isNight(System.currentTimeMillis())) {
            if (type == CommonConstants.CLEAR)
                return R.drawable.w_nightsunny_big;
            switch (type / 100) {
                case CommonConstants.THUNDERSTORM:
                    return R.drawable.w_nightthundeshower_big;
                case CommonConstants.DRIZZLE:
                    return R.drawable.w_nightrain_big;
                case CommonConstants.RAIN:
                    return R.drawable.w_nightrain_big;
                case CommonConstants.SNOW:
                    return R.drawable.w_nightsnow_big;
                case CommonConstants.ATMOSPHERE:
                    return R.drawable.bg_api;
                case CommonConstants.CLOUDS:
                    return R.drawable.w_sadcloud;
                case CommonConstants.EXTREME:
                    return R.drawable.w_dustblowing_big;
                default:
                    return R.drawable.w_default;
            }
        } else {
            // 如果是白天
            if (type == CommonConstants.CLEAR)
                return R.drawable.w_sunny_big;
            switch (type / 100) {
                case CommonConstants.THUNDERSTORM:
                    return R.drawable.w_thundeshower_big;
                case CommonConstants.DRIZZLE:
                    return R.drawable.w_lightrain_big;
                case CommonConstants.RAIN:
                    return R.drawable.w_heavyrain_big;
                case CommonConstants.SNOW:
                    return R.drawable.w_snow_big;
                case CommonConstants.ATMOSPHERE:
                    return R.drawable.bg_api;
                case CommonConstants.CLOUDS:
                    return R.drawable.w_sadcloud;
                case CommonConstants.EXTREME:
                    return R.drawable.w_dustblowing_big;
                default:
                    return R.drawable.w_default;
            }
        }
    }

    public static boolean isNight(long time) {
        SimpleDateFormat df = new SimpleDateFormat("HH");
        String timeStr = df.format(new Date(System.currentTimeMillis()));
        try {
            int timeHour = Integer.parseInt(timeStr);
            return (timeHour >= 18 || timeHour <= 6);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return false;
    }

}
