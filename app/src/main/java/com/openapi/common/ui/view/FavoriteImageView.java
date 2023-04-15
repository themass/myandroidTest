//package com.qq.common.ui.view;
//
//import android.content.Context;
//import android.support.v7.widget.AppCompatImageView;
//import android.util.AttributeSet;
//
//import com.openapi.ks.free1.R;
//import com.qq.myapp.bean.vo.FavoriteVo;
//
//public class FavoriteImageView extends AppCompatImageView  implements FavoriteUtil.GetFavoriteListener, FavoriteUtil.ModFavoriteListener{
//    public FavoriteImageView(Context context) {
//        super(context);
//
//    }
//
//    public FavoriteImageView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//    }
//
//    public FavoriteImageView(Context context, AttributeSet attrs, int defStyleAttr) {
//        super(context,attrs,defStyleAttr);
//    }
//    public void clickFavorite(final FavoriteVo vo){
//        FavoriteUtil.modLocalFavoritesAsync(getContext(), vo, FavoriteImageView.this);
//    }
//    public  void initSrc(String url){
//        FavoriteUtil.getLocalFavoritesAsync(getContext(), url, this);
//    }
//    @Override
//    public void modFavorite(boolean ret) {
//        modFavoriteBg(ret);
//    }
//
//    @Override
//    public void isFavorite(String itemUrl, boolean ret) {
//        modFavoriteBg(ret);
//    }
//    public void modFavoriteBg(boolean ret) {
//        if (ret) {
//            setBackgroundResource(R.drawable.ic_menu_favorite_ed);
//        } else {
//            setBackgroundResource(R.drawable.ic_menu_favorite_no);
//        }
//    }
//}
