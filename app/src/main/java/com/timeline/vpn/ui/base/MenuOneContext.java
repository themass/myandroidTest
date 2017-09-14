package com.timeline.vpn.ui.base;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.timeline.vpn.R;

/**
 * Created by themass on 2017/9/13.
 */

public class MenuOneContext {
    public static void showOneMenu(Context context, View v,int title,final MyOnMenuItemClickListener listener,final int position){
        PopupMenu popupMenu = new PopupMenu(context, v);
        popupMenu.getMenuInflater().inflate(R.menu.menu_oneline, popupMenu.getMenu());
        MenuItem item = popupMenu.getMenu().findItem(R.id.menu_one);
        item.setTitle(title);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if(listener!=null){
                    return listener.onMenuItemClick(item,position);
                }
                return false;
            }
        });
        popupMenu.show();
    }
    public static interface MyOnMenuItemClickListener{
        public boolean onMenuItemClick(MenuItem item,int position);
    }
}
