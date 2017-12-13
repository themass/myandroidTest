package com.way.yahoo;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.timeline.sex.R;
import com.timeline.sex.bean.form.IwannaForm;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.BaseService;
import com.timeline.sex.data.UserLoginUtil;
import com.timeline.sex.ui.user.LoginActivity;
import com.way.common.util.T;
import com.way.ui.swipeback.SwipeBackActivity;

import static android.content.ContentValues.TAG;

public class FeedBackActivity extends SwipeBackActivity {
	private EditText mFeedBackEt;
	private Button mSendBtn;
	BaseService indexService;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.feed_back_view);
		indexService = new BaseService();
		indexService.setup(this);
		((TextView) findViewById(R.id.city_title)).setText("信息反馈");
		findViewById(R.id.back_image).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				finish();
			}
		});
		mFeedBackEt = (EditText) findViewById(R.id.fee_back_edit);
		mSendBtn = (Button) findViewById(R.id.feed_back_btn);
		mSendBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String content = mFeedBackEt.getText().toString();
				if (!TextUtils.isEmpty(content)) {
					if (UserLoginUtil.getUserCache() == null) {
						Intent intent = new Intent(FeedBackActivity.this,LoginActivity.class);
						startActivity(intent);
						return;
					}
					indexService.postData(Constants.getUrl(Constants.API_IWANNA_URL), new IwannaForm(content), null, null, TAG, null);
                    FeedBackActivity.this.finish();
				} else {
					T.showShort(FeedBackActivity.this, "亲,多说几句嘛!么么哒！");
				}
			}
		});
	}
}
