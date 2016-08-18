package com.timeline.vpn.weather;

import android.content.Context;
import android.text.TextUtils;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.LocationClientOption.LocationMode;

public class LocationUtils {
	private static final int MAX_TRY_COUNT = 5;
	private LocationClient mLocationClient = null;
	private LocationListener mListener;
	private int mTryCount;

	public static abstract interface LocationListener {
		public abstract void detecting();

		public abstract void succeed(String city);

		public abstract void failed();
	}

	public LocationUtils(Context context, LocationListener listener) {
		if (listener == null)
			new NullPointerException("LocationListener can't be null");
		mListener = listener;
		mLocationClient = new LocationClient(context);
		initLocation();
		mLocationClient.registerLocationListener(mLocationListener);
	}
	private void initLocation(){
		LocationClientOption option = new LocationClientOption();
		option.setLocationMode(LocationMode.Hight_Accuracy
		);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
		option.setCoorType("bd09ll");//可选，默认gcj02，设置返回的定位结果坐标系
		int span=1000;
		option.setScanSpan(span);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
		option.setIsNeedAddress(true);//可选，设置是否需要地址信息，默认不需要
		option.setOpenGps(true);//可选，默认false,设置是否使用gps
		option.setLocationNotify(true);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
		option.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
		option.setIsNeedLocationPoiList(true);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
		option.setIgnoreKillProcess(false);//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
		option.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集
		option.setEnableSimulateGps(false);//可选，默认false，设置是否需要过滤gps仿真结果，默认需要
		mLocationClient.setLocOption(option);
	}
	// 开始定位
	public void startLocation() {
		mLocationClient.start();
		mListener.detecting();
		mTryCount = 0;
	}

	// 结束定位
	public void stopLocation() {
		mLocationClient.stop();
		mTryCount = 0;
	}

	public boolean isStarted() {
		return mLocationClient.isStarted();
	}

	private LocationClientOption getLocationClientOption(Context context) {
		LocationClientOption option = new LocationClientOption();
		option.setOpenGps(true);
		option.setLocationMode(LocationMode.Hight_Accuracy);// 设置定位模式
		option.setCoorType("gcj02");// 返回的定位结果是百度经纬度，默认值gcj02
		option.setScanSpan(1000);// 设置发起定位请求的间隔时间为1000ms
		option.setProdName(context.getPackageName());
		option.setIsNeedAddress(true);
		return option;
	}

	/**
	 * 实现定位回调监听
	 */
	BDLocationListener mLocationListener = new BDLocationListener() {

		@Override
		public void onReceiveLocation(BDLocation location) {
			if (location == null || location.getLocType() != 161
					|| TextUtils.isEmpty(location.getCity())) {
				mTryCount++;
				if (mTryCount >= MAX_TRY_COUNT) {
					mListener.failed();
					stopLocation();
				}
				return;
			}
			String city = location.getCity().replace("市", "");
			mListener.succeed(city);
			stopLocation();// 停止定位
		}

	};
}
