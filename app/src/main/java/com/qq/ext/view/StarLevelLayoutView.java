package com.qq.ext.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.qq.network.R;


public class StarLevelLayoutView extends LinearLayout {

	public StarLevelLayoutView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public StarLevelLayoutView(Context context) {
		super(context);
	}

	@SuppressLint("NewApi")
	public StarLevelLayoutView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public void setRating(int rating) {
		removeAllViews();
		int tempRating = rating;
		if (tempRating == 0) {
			tempRating = 5;
		}
		int i = 0;
		while (i < 5) {
			ImageView imageView = new ImageView(getContext());
			LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT);
			if (i < tempRating) {
				imageView.setImageResource(R.drawable.mintegral_demo_star_sel);
			} else {
				imageView.setImageResource(R.drawable.mintegral_demo_star_nor);
			}
			addView(imageView, lp);
			i++;
		}
	}

}
