package com.openapi.ks.myapp.ui.base;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.openapi.ks.moviefree1.R;

import java.lang.reflect.Method;

/**
 * Created by openapi on 2017/9/13.
 */

public class MenuOneContext {
    public static void showOneMenu(Context context, View v,int title,int icon,final MyOnMenuItemClickListener listener,final int position){
        PopupMenu popupMenu = new PopupMenu(context, v);
        popupMenu.getMenuInflater().inflate(R.menu.menu_oneline, popupMenu.getMenu());
        MenuItem item = popupMenu.getMenu().findItem(R.id.menu_one);
        item.setTitle(title);
        item.setIcon(icon);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if(listener!=null){
                    return listener.onMenuItemClick(item,position);
                }
                return false;
            }
        });
        setIconEnable(popupMenu.getMenu(),true);
        popupMenu.show();
//        MenuPopupHelper menuHelper = new MenuPopupHelper(context, (MenuBuilder) popupMenu.getMenu(), v);
//        menuHelper.setForceShowIcon(true);
//        menuHelper.show();
    }
    private static void setIconEnable(Menu menu, boolean enable)
    {
        try
        {
            Class<?> clazz = Class.forName("com.android.internal.view.menu.MenuBuilder");
            Method m = clazz.getDeclaredMethod("setOptionalIconsVisible", boolean.class);
            m.setAccessible(true);
            //传入参数
            m.invoke(menu, enable);

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    public static interface MyOnMenuItemClickListener{
        public boolean onMenuItemClick(MenuItem item,int position);
    }
}
