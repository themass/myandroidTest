package com.timeline.vpn.weather.bean;

import java.util.List;

public class WeatherInfo {
    public WeatherSys sys;
    public  String name;
    public long dt;
    public  WeatherClouds clouds;
    public String base;
    public WeatherMain main;
    public WeatherWind wind;
    public List<WeatherW> weather;
}
