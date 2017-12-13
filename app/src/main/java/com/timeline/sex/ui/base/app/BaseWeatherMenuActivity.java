package com.timeline.sex.ui.base.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.sspacee.common.CommonConstants;
import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.common.weather.LocationUtils;
import com.sspacee.common.weather.WeatherIconUtils;
import com.sspacee.common.weather.WeatherSpider;
import com.sspacee.common.weather.bean.WeatherInfo;
import com.sspacee.yewu.net.NetUtils;
import com.sspacee.yewu.net.VolleyUtils;
import com.sspacee.yewu.net.request.StringRequest;
import com.timeline.sex.R;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.StaticDataUtil;
import com.way.yahoo.WayMainActivity;

/**
 * Created by themass on 2016/3/1.
 */
public abstract class BaseWeatherMenuActivity extends LogActivity {
    public ImageView ivWeather;
    public ImageView ivLocation;
    LocationUtils.LocationListener mCityNameStatus = new LocationUtils.LocationListener() {
        @Override
        public void detecting() {
            LogUtil.i("detecting...");
            setWeatherIcon(R.drawable.w_default);
        }

        @Override
        public void succeed(double lat, double lon) {
            StaticDataUtil.add(Constants.LON, lon);
            StaticDataUtil.add(Constants.LAT, lat);
            startGetWeather(lat, lon);
        }

        @Override
        public void failed() {
            setWeatherIcon(R.drawable.w_default);
            LogUtil.e(getString(R.string.error_getlocation_fail));
//            Toast.makeText(BaseWeatherMenuActivity.this, R.string.error_getlocation_fail,
//                    Toast.LENGTH_SHORT).show();
        }
    };
    private WeatherInfo weatherInfo;
    private LocationUtils mLocationUtils;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBusUtil.getEventBus().unregister(this);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (ivWeather == null) {
            getMenuInflater().inflate(R.menu.menu_toolbar, menu);
            LogUtil.i("onCreateOptionsMenu");
            MenuItem menuWeather = menu.findItem(R.id.menu_weather);
            menuWeather.setActionView(R.layout.common_actionbar_image_view);
            ivWeather = (ImageView) menuWeather.getActionView().findViewById(R.id.iv_menu);
            menuWeather.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LogUtil.i("weatherClick");
                    startLocation(mCityNameStatus);
                    Intent intent = new Intent(BaseWeatherMenuActivity.this,WayMainActivity.class);
                    startActivity(intent);
                }
            });
            if (StaticDataUtil.get(CommonConstants.WEATHER_KEY, WeatherInfo.class) != null) {
                weatherInfo = StaticDataUtil.get(CommonConstants.WEATHER_KEY, WeatherInfo.class);
                if (weatherInfo != null && !CollectionUtils.isEmpty(weatherInfo.weather)) {
                    setWeatherIcon(WeatherIconUtils.getWeatherIcon(weatherInfo.weather.get(0).id));
                }
            } else {
                startLocation(mCityNameStatus);
            }
        }
        return true;
    }
    public void setWeatherIcon(int id) {
        if (ivWeather != null) {
            ivWeather.setImageResource(id);
        }
    }

    private void startGetWeather(double lat, double lon) {
        WeatherInfoListener listener = new WeatherInfoListener();
        StringRequest request = new StringRequest(this, String.format(
                WeatherSpider.WEATHER_ALL, String.valueOf(lat), String.valueOf(lon), SystemUtils.getLang(this)), listener, listener);
        VolleyUtils.addRequest(request);
    }

    protected void startLocation(LocationUtils.LocationListener cityNameStatus) {
        if (!NetUtils.checkNetwork(this)) {
            ToastUtil.showShort(R.string.error_network_no_connection);
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
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i("onOptionsMenuClosed");
        stopLocation();
    }

    class WeatherInfoListener implements Response.Listener<String>, Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError volleyError) {
            VolleyUtils.showVolleyError(volleyError);
            setWeatherIcon(R.drawable.w_default);
        }

        @Override
        public void onResponse(String result) {
            try {
                weatherInfo = WeatherSpider.getWeatherInfo(
                        BaseWeatherMenuActivity.this, result);
                if (weatherInfo != null && !CollectionUtils.isEmpty(weatherInfo.weather)) {
                    StaticDataUtil.add(CommonConstants.WEATHER_KEY, weatherInfo);
                    setWeatherIcon(WeatherIconUtils.getWeatherIcon(weatherInfo.weather.get(0).id));
                }
            } catch (Exception e) {
                setWeatherIcon(R.drawable.w_default);
                LogUtil.e(result, e);
            }
        }
    }
}
