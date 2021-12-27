package com.qq.yewu.ads.mobvista;

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mintegral.msdk.MIntegralConstans;
import com.mintegral.msdk.MIntegralSDK;
import com.mintegral.msdk.out.MIntegralSDKFactory;
import com.mintegral.msdk.out.MtgWallHandler;
import com.qq.common.util.LogUtil;
import com.qq.ks1.R;
import com.qq.myapp.constant.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dengt on 2017/9/20.
 */

public class WallMobvAds {
    private MtgWallHandler mtgHandler ;
    private Map<String, Object> properties;
    public void load(final FragmentActivity context,ViewGroup viewGroup){
        try{
            viewGroup.removeAllViews();
            properties = MtgWallHandler.getWallProperties(Constants.Mob_UNIT_WALL);
//            // user bitmap resId as the logo
//            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_LOGO, bitmap);
            // user drawable resId as the logo
//            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_LOGO_ID, R.drawable.ic_launcher);

            // use bitmap or text as the appwall title
//            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_LOGO_TEXT, bitmap);
            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_LOGO_TEXT, "广告也精彩");
//            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_LOGO_TEXT_SIZE, "20dp");
            // use color resId as the appwall title
//            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_BACKGROUND_COLOR, R.color.mintegral_green);
            // use drawable resid as the appwall title
//            properties.put(MIntegralConstans.PROPERTIES_WALL_TITLE_BACKGROUND_ID, R.drawable.ic_launcher);

            // wall main background must be color
            properties.put(MIntegralConstans.PROPERTIES_WALL_MAIN_BACKGROUND_ID, R.color.mintegral_bg_main);

            // wall tab background must be color
            properties.put(MIntegralConstans.PROPERTIES_WALL_TAB_BACKGROUND_ID, R.color.mintegral_bg_main);

            // wall tab indicater line must be color
            properties.put(MIntegralConstans.PROPERTIES_WALL_TAB_INDICATE_LINE_BACKGROUND_ID,
                    R.color.mintegral_wall_tab_line);

            // wall button color must be drawable
//            properties.put(MIntegralConstans.PROPERTIES_WALL_BUTTON_BACKGROUND_ID, R.drawable.mintegral_shape_btn);
//
//            // wall loading view
//            properties.put(MIntegralConstans.PROPERTIES_WALL_LOAD_ID, R.layout.mintegral_demo_wall_click_loading);

            properties.put(MIntegralConstans.PROPERTIES_WALL_STATUS_COLOR, R.color.mintegral_green);

            properties.put(MIntegralConstans.PROPERTIES_WALL_NAVIGATION_COLOR, R.color.mintegral_green);

            // set the wall tab color of selected and unselected text by hex color
            // codes
            properties.put(MIntegralConstans.PROPERTIES_WALL_TAB_SELECTED_TEXT_COLOR, "#ff7900");
            properties.put(MIntegralConstans.PROPERTIES_WALL_TAB_UNSELECTED_TEXT_COLOR, "#ffaa00");
            mtgHandler = new MtgWallHandler(properties, context, viewGroup);
            // customer entry layout begin The part of the code can not
            View view = LayoutInflater.from(context).inflate(R.layout.customer_entry, null);
            view.findViewById(R.id.imageview).setTag(MIntegralConstans.WALL_ENTRY_ID_IMAGEVIEW_IMAGE);
            view.findViewById(R.id.newtip_area).setTag(MIntegralConstans.WALL_ENTRY_ID_VIEWGROUP_NEWTIP);
            mtgHandler.setHandlerCustomerLayout(view);
            // customer entry layout end */
            mtgHandler.load();
        } catch (Throwable e) {
            LogUtil.e(e);
         }
    }
    /**
     * Preloading the appwall can improve the revenue for you.
     */
    public void preloadWall(Context context) {
        MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
        Map<String, Object> preloadMap = new HashMap<String, Object>();
        preloadMap.put(MIntegralConstans.PROPERTIES_LAYOUT_TYPE, MIntegralConstans.LAYOUT_APPWALL);
        preloadMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_WALL);
        sdk.preload(preloadMap);
    }

    /**
     * open the appwall via intent
     */
    public void openWall(Context context) {
        try {
            Map<String, Object> properties = MtgWallHandler.getWallProperties(Constants.Mob_UNIT_WALL);
            MtgWallHandler mtgHandler = new MtgWallHandler(properties, context);
            mtgHandler.startWall();
        } catch (Exception e) {
            Log.e("MTGActivity", "", e);
        }
    }



}
