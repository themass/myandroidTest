package com.timeline.vpn.ui.base;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.City;
import com.timeline.vpn.common.net.NetUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.StringRequest;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.provider.CityProvider;
import com.timeline.vpn.weather.LocationUtils;
import com.timeline.vpn.weather.WeatherIconUtils;
import com.timeline.vpn.weather.WeatherSpider;
import com.timeline.vpn.weather.bean.WeatherInfo;

/**
 * Created by gqli on 2016/3/1.
 */
public abstract class BaseWeatherMenuFragment extends LogActivity {
    private City city;
    private WeatherInfo weatherInfo;
    ImageView  ivWeather;
    protected ContentResolver mContentResolver;
    private LocationUtils mLocationUtils;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(ivWeather==null) {
            getMenuInflater().inflate(R.menu.menu_space, menu);
            LogUtil.i("onCreateOptionsMenu");
            MenuItem menuWeather = menu.findItem(R.id.menu_weather);
            menuWeather.setActionView(R.layout.menu_weather_view);
            ivWeather = (ImageView) menuWeather.getActionView().findViewById(R.id.iv_weather);
            menuWeather.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LogUtil.i("weatherClick");
                    startLocation(mCityNameStatus);
                }
            });
            if(StaticDataUtil.get(Constants.WEATHER_KEY,WeatherInfo.class)!=null){
                weatherInfo = StaticDataUtil.get(Constants.WEATHER_KEY, WeatherInfo.class);
                setWeatherIcon(WeatherIconUtils.getWeatherIcon(weatherInfo.getRealTime().getAnimation_type()));
            }else {
                startLocation(mCityNameStatus);
            }
        }
        return true;
    }
    public void setWeatherIcon(int id){
        if(ivWeather!=null){
            ivWeather.setImageResource(id);
        }
    }
    LocationUtils.LocationListener mCityNameStatus = new LocationUtils.LocationListener() {
        @Override
        public void detecting() {
            LogUtil.i("detecting...");
            setWeatherIcon(R.drawable.ic_default_big);
        }

        @Override
        public void succeed(String name) {
            LogUtil.i(name);
            city = getLocationCityFromDB(name);
            StaticDataUtil.add(Constants.CITY_KEY,city);
            if (city==null  || TextUtils.isEmpty(city.getPostID())) {
                setWeatherIcon(R.drawable.ic_default_big);
                Toast.makeText(BaseWeatherMenuFragment.this, R.string.error_no_city,
                        Toast.LENGTH_SHORT).show();
            } else {
                LogUtil.i("location" + city.toString());
                startGetWeather();
            }
        }
        @Override
        public void failed() {
            setWeatherIcon(R.drawable.ic_default_big);
            Toast.makeText(BaseWeatherMenuFragment.this, R.string.error_getlocation_fail,
                    Toast.LENGTH_SHORT).show();
        }
    };
    private void startGetWeather(){
        WeatherInfoListener listener = new WeatherInfoListener();
        StringRequest request = new StringRequest(String.format(
                WeatherSpider.WEATHER_ALL, city.getPostID()), listener, listener);
        VolleyUtils.addRequest(request);
    }
    protected City getLocationCityFromDB(String name) {
        City city = new City();
        city.setName(name);
        Cursor c = mContentResolver.query(CityProvider.CITY_CONTENT_URI,
                new String[] { CityProvider.CityConstants.POST_ID }, CityProvider.CityConstants.NAME
                        + "=?", new String[] { name }, null);
        if (c != null && c.moveToNext())
            city.setPostID(c.getString(c.getColumnIndex(CityProvider.CityConstants.POST_ID)));
        return city;
    }
    protected void startLocation(LocationUtils.LocationListener cityNameStatus) {
        if (!NetUtils.checkNetwork(this)) {
            Toast.makeText(this, R.string.error_network_no_connection, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mLocationUtils == null)
            mLocationUtils = new LocationUtils(this, cityNameStatus);
        if (!mLocationUtils.isStarted()) {
            mLocationUtils.startLocation();// 开始定位
        }
    }
    protected void stopLocation() {
        if (mLocationUtils != null && mLocationUtils.isStarted())
            mLocationUtils.stopLocation();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContentResolver();
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i("onOptionsMenuClosed");
        stopLocation();
    }

    class WeatherInfoListener implements Response.Listener<String>,Response.ErrorListener{
        @Override
        public void onErrorResponse(VolleyError volleyError) {
            VolleyUtils.showVolleyError(volleyError);
            setWeatherIcon(R.drawable.ic_default_big);
        }

        @Override
        public void onResponse(String result) {
            try {
                weatherInfo = WeatherSpider.getWeatherInfo(
                        BaseWeatherMenuFragment.this, city.getPostID(), result);
                StaticDataUtil.add(Constants.WEATHER_KEY,weatherInfo);
                setWeatherIcon(WeatherIconUtils.getWeatherIcon(weatherInfo.getRealTime().getAnimation_type()));
            }catch (Exception e){
                setWeatherIcon(R.drawable.ic_default_big);
                LogUtil.e(result,e);
            }
        }
    }
}
