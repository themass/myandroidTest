package com.timeline.vpn.weather;

import android.content.Context;

import com.timeline.vpn.common.util.GsonUtils;
import com.timeline.vpn.weather.bean.WeatherInfo;


public class WeatherSpider {
    public static final String WEATHER_ALL = "http://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=baf48357f8683e3764739341d792a0ad&lang=%s";

    public static WeatherInfo getWeatherInfo(Context context, String result) {
        return GsonUtils.getInstance().fromJson(result, WeatherInfo.class);
    }

}