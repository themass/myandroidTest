package com.openai.commonview.view;

import android.content.Context;

import androidx.appcompat.widget.AppCompatImageView;

import android.util.AttributeSet;

import com.openai.commonevent.FavoriteVo;
import com.openai.commonutil.FavoriteUtil;
import com.openai.commonutil.R;

public class FavoriteImageView extends AppCompatImageView implements FavoriteUtil.GetFavoriteListener, FavoriteUtil.ModFavoriteListener {
    public FavoriteImageView(Context context) {
        super(context);

    }

    public FavoriteImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FavoriteImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void clickFavorite(final FavoriteVo vo) {
        FavoriteUtil.modLocalFavoritesAsync(getContext(), vo, FavoriteImageView.this);
    }

    public void initSrc(String url) {
        FavoriteUtil.getLocalFavoritesAsync(getContext(), url, this);
    }

    @Override
    public void modFavorite(FavoriteVo vo) {
        modFavoriteBg(vo!=null);
    }

    @Override
    public void isFavorite(String itemUrl, FavoriteVo vo) {
        modFavoriteBg(vo!=null);
    }

    public void modFavoriteBg(boolean ret) {
        if (ret) {
            setImageResource(R.drawable.ic_menu_favorite_ed);
        } else {
            setImageResource(R.drawable.ic_menu_favorite_no);
        }
    }
}
