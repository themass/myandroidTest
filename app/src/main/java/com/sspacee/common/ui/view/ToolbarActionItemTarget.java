//package com.sspacee.common.ui.view;
//
//import android.graphics.Point;
//import android.support.annotation.IdRes;
//import android.support.v7.widget.Toolbar;
//import android.view.View;
//
//import com.sspacee.common.util.LogUtil;
//
//import java.lang.reflect.Field;
//
//public class ToolbarActionItemTarget implements Target {
//
//    private Toolbar toolbar;
//    private int menuItemId;
//    private View view;
//
//    public ToolbarActionItemTarget(Toolbar toolbar, @IdRes int itemId) {
//        this.toolbar = toolbar;
//        this.menuItemId = itemId;
//    }
//    public ToolbarActionItemTarget(Toolbar toolbar) {
//        this.toolbar = toolbar;
//        Field field = null;
//        try {
//            field =Toolbar.class.getDeclaredField("mNavButtonView");
//            field.setAccessible(true);
//            view = (View) field.get(toolbar);
//        } catch (NoSuchFieldException e) {
//            LogUtil.e(e);
//        } catch (IllegalAccessException e) {
//            LogUtil.e(e);
//        }
//
//    }
//    @Override
//    public Point getPoint() {
//        if(menuItemId>0)
//            return new ViewTarget(toolbar.findViewById(menuItemId)).getPoint();
//        if(view!=null)
//            return new ViewTarget(view).getPoint();
//        return new Point();
//    }
//
//}