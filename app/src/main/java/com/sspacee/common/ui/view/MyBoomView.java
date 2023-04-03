package com.sspacee.common.ui.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.ks.myapp.bean.vo.FavoriteVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.data.config.ConfigActionEvent;
import com.ks.sexfree1.R;
import com.nightonke.boommenu.BoomButtons.SimpleCircleButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;

import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by themass on 2016/9/6.
 */
public class MyBoomView extends FrameLayout {

    @Nullable
    @BindView(R.id.bmb_tool)
    BoomMenuButton boomMenuButton;

    private static int imageResourceIndex = 0;

    private static int[] imageResources = new int[]{
            R.drawable.ic_menu_share,
            R.drawable.ic_browser1,
            R.drawable.ic_browser1,
            R.drawable.ic_menu_favorite_no,
    };


    public MyBoomView(Context context) {
        super(context);
        setupView();
    }

    public MyBoomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public MyBoomView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }

    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.layout_boom_view, null);
        addView(myView);
        ButterKnife.bind(this);
        for (int i = 0; i < boomMenuButton.getPiecePlaceEnum().pieceNumber(); i++)
            boomMenuButton.addBuilder(getSimpleCircleButtonBuilder());
    }


    public static TextOutsideCircleButton.Builder getSimpleCircleButtonBuilder() {
        return new TextOutsideCircleButton.Builder()
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.location_country)
                .pieceColor(Color.WHITE)
                ;

    }


    static int getImageResource() {
        if (imageResourceIndex >= imageResources.length) imageResourceIndex = 0;
        return imageResources[imageResourceIndex++];
    }

    public interface OnFavoriteItemClick {

        FavoriteVo getFavoriteDataUrl();
        String getBrowserDatUrl();

    }

}
