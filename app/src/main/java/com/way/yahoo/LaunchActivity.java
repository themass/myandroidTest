package com.way.yahoo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ads.base.AdsManager;
import com.qq.sexfree.R;


/**
 * Created by dengt on 2016/3/22.
 */
public class LaunchActivity extends FragmentActivity {
    public static final int SKIP_SLOW= -1;
    public static final int STARTUP_SHOW_TIME_3000 = 3000;
    RelativeLayout ivAds;
    RelativeLayout skipView;
    TextView tvShu;
    private int max = STARTUP_SHOW_TIME_3000;
    private int now = 0;
    private Window mWindow;

    private Runnable mStartMainRunnable = new Runnable() {
        @Override
        public void run() {
            launch();
        }
    };
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            now = now + 1000;
            if (now < max) {
                tvShu.setText((max - now) / 1000 + " s");
                delay1s();
            } else {
                tvShu.setText(R.string.skip);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mWindow = getWindow();
        WindowManager.LayoutParams params = mWindow.getAttributes();
        params.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        mWindow.setAttributes(params);

        setContentView(R.layout.main_launch);
        ivAds = findViewById(R.id.rl_spread);
        skipView = findViewById(R.id.skip_view);
        tvShu = (TextView) findViewById(R.id.tv_shu);
        tvShu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch();
            }
        });
    }

    private void launch() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    protected void onResume() {
        super.onResume();
        delay1s();
        AdsManager.getInstans().showSplashAds(this,ivAds,skipView);
        mHandler.postDelayed(mStartMainRunnable, STARTUP_SHOW_TIME_3000);
    }

    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(SKIP_SLOW, 1000);
    }


    @Override
    public void onDestroy() {
        mHandler.removeMessages(SKIP_SLOW);
        mHandler.removeCallbacks(mStartMainRunnable);
        super.onDestroy();
    }
}
