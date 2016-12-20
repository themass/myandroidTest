package com.timeline.vpn.ui.base;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.timeline.vpn.R;
import com.timeline.vpn.common.net.NetUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.StringRequest;
import com.timeline.vpn.common.util.CollectionUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.weather.LocationUtils;
import com.timeline.vpn.weather.WeatherIconUtils;
import com.timeline.vpn.weather.WeatherSpider;
import com.timeline.vpn.weather.bean.WeatherInfo;

/**
 * Created by themass on 2016/3/1.
 */
public abstract class BaseWeatherMenuActivity extends LogActivity {
    ImageView ivWeather;
    LocationUtils.LocationListener mCityNameStatus = new LocationUtils.LocationListener() {
        @Override
        public void detecting() {
            LogUtil.i("detecting...");
            setWeatherIcon(R.drawable.w__default);
        }

        @Override
        public void succeed(double lat, double lon) {
            StaticDataUtil.add(Constants.LON,lon);
            StaticDataUtil.add(Constants.LAT,lat);
            startGetWeather(lat, lon);
        }

        @Override
        public void failed() {
            setWeatherIcon(R.drawable.w__default);
            Toast.makeText(BaseWeatherMenuActivity.this, R.string.error_getlocation_fail,
                    Toast.LENGTH_SHORT).show();
        }
    };
    private WeatherInfo weatherInfo;
    private LocationUtils mLocationUtils;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (ivWeather == null) {
            getMenuInflater().inflate(R.menu.menu_space, menu);
            LogUtil.i("onCreateOptionsMenu");
            MenuItem menuWeather = menu.findItem(R.id.menu_view);
            menuWeather.setActionView(R.layout.common_image_view);
            ivWeather = (ImageView) menuWeather.getActionView().findViewById(R.id.iv_menu);
            menuWeather.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LogUtil.i("weatherClick");
                    startLocation(mCityNameStatus);
                }
            });
            if (StaticDataUtil.get(Constants.WEATHER_KEY, WeatherInfo.class) != null) {
                weatherInfo = StaticDataUtil.get(Constants.WEATHER_KEY, WeatherInfo.class);
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
        StringRequest request = new StringRequest(String.format(
                WeatherSpider.WEATHER_ALL, String.valueOf(lat), String.valueOf(lon), SystemUtils.getLang(this)), listener, listener);
        VolleyUtils.addRequest(request);
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
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i("onOptionsMenuClosed");
        stopLocation();
    }

    class WeatherInfoListener implements Response.Listener<String>, Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError volleyError) {
            VolleyUtils.showVolleyError(volleyError);
            setWeatherIcon(R.drawable.w__default);
        }

        @Override
        public void onResponse(String result) {
            try {
                weatherInfo = WeatherSpider.getWeatherInfo(
                        BaseWeatherMenuActivity.this, result);
                if (weatherInfo != null && !CollectionUtils.isEmpty(weatherInfo.weather)) {
                    StaticDataUtil.add(Constants.WEATHER_KEY, weatherInfo);
                    setWeatherIcon(WeatherIconUtils.getWeatherIcon(weatherInfo.weather.get(0).id));
                }
            } catch (Exception e) {
                setWeatherIcon(R.drawable.w__default);
                LogUtil.e(result, e);
            }
        }
    }
}
