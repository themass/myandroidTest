package com.sspacee.common.weather;

import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;

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
            if (type == Constants.CLEAR)
                return R.drawable.w_nightsunny_big;
            switch (type / 100) {
                case Constants.THUNDERSTORM:
                    return R.drawable.w_nightthundeshower_big;
                case Constants.DRIZZLE:
                    return R.drawable.w_nightrain_big;
                case Constants.RAIN:
                    return R.drawable.w_nightrain_big;
                case Constants.SNOW:
                    return R.drawable.w_nightsnow_big;
                case Constants.ATMOSPHERE:
                    return R.drawable.bg_api;
                case Constants.CLOUDS:
                    return R.drawable.w_sadcloud;
                case Constants.EXTREME:
                    return R.drawable.w_dustblowing_big;
                default:
                    return R.drawable.w__default;
            }
        } else {
            // 如果是白天
            if (type == Constants.CLEAR)
                return R.drawable.w_sunny_big;
            switch (type / 100) {
                case Constants.THUNDERSTORM:
                    return R.drawable.w_thundeshower_big;
                case Constants.DRIZZLE:
                    return R.drawable.w_lightrain_big;
                case Constants.RAIN:
                    return R.drawable.w_heavyrain_big;
                case Constants.SNOW:
                    return R.drawable.w_snow_big;
                case Constants.ATMOSPHERE:
                    return R.drawable.bg_api;
                case Constants.CLOUDS:
                    return R.drawable.w_sadcloud;
                case Constants.EXTREME:
                    return R.drawable.w_dustblowing_big;
                default:
                    return R.drawable.w__default;
            }
        }
    }

    /**
     * 获取天气清晰背景
     *
     * @param type
     * @return
     */
    public static int getWeatherNromalBg(int type) {
        if (isNight(System.currentTimeMillis())) {
            if (type == Constants.CLEAR)
                return R.drawable.bg_fine_night;
            switch (type / 100) {
                case Constants.THUNDERSTORM:
                case Constants.DRIZZLE:
                case Constants.RAIN:
                    return R.drawable.bg_rain;
                case Constants.SNOW:
                    return R.drawable.w_nightsnow_big;
                case Constants.ATMOSPHERE:
                case Constants.CLOUDS:
                    return R.drawable.bg_cloudy_night;
                case Constants.EXTREME:
                    return R.drawable.bg_haze_blur;
                default:
                    return R.drawable.bg_cloudy_night;
            }
        } else {
            // 如果是白天
            if (type == Constants.CLEAR)
                return R.drawable.bg_fine_day;
            switch (type / 100) {
                case Constants.THUNDERSTORM:
                case Constants.DRIZZLE:
                case Constants.RAIN:
                    return R.drawable.w_heavyrain_big;
                case Constants.SNOW:
                    return R.drawable.bg_snow_night;
                case Constants.ATMOSPHERE:
                    return R.drawable.bg_overcast;
                case Constants.CLOUDS:
                    return R.drawable.bg_cloudy_day;
                case Constants.EXTREME:
                    return R.drawable.bg_sand_storm_blur;
                default:
                    return R.drawable.bg_cloudy_day;
            }
        }
    }

    public static boolean isNight(long time) {
        SimpleDateFormat df = new SimpleDateFormat("HH");
        String timeStr = df.format(new Date(System.currentTimeMillis()));
        // L.i("liweiping", "timeStr = " + timeStr);
        try {
            int timeHour = Integer.parseInt(timeStr);
            return (timeHour >= 18 || timeHour <= 6);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return false;
    }

}
