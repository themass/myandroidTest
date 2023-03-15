package com.sspacee.common.ui.view;

import android.content.Context;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;

import com.ks.sexfree1.R;
import com.ks.myapp.bean.vo.FavoriteVo;
import com.ks.myapp.data.FavoriteUtil;

public class FavoriteImageView extends AppCompatImageView  implements FavoriteUtil.GetFavoriteListener, FavoriteUtil.ModFavoriteListener{
    public FavoriteImageView(Context context) {
        super(context);

    }

    public FavoriteImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FavoriteImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context,attrs,defStyleAttr);
    }
    public void clickFavorite(final FavoriteVo vo){
        FavoriteUtil.modLocalFavoritesAsync(getContext(), vo, FavoriteImageView.this);
    }
    public  void initSrc(String url){
        FavoriteUtil.getLocalFavoritesAsync(getContext(), url, this);
    }
    @Override
    public void modFavorite(boolean ret) {
        modFavoriteBg(ret);
    }

    @Override
    public void isFavorite(String itemUrl, boolean ret) {
        modFavoriteBg(ret);
    }
    public void modFavoriteBg(boolean ret) {
        if (ret) {
            setImageResource(R.drawable.ic_menu_favorite_ed);
        } else {
            setImageResource(R.drawable.ic_menu_favorite_no);
        }
    }
}
