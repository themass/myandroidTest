package com.openapi.ks.myapp.ui.gsy;

import android.app.Activity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ToastUtil;
import com.openapi.feature.video.R;
import com.openapi.ks.myapp.constant.Routes;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;

/**
 * 详情页「更多」菜单：倍速和解码设置入口（从 {@link DetailGSYVideoPlayer} 拆出）。
 */
final class PlayerMoreMenu {

    private PlayerMoreMenu() {
    }

    static void show(DetailGSYVideoPlayer player) {
        Activity act = CommonUtil.scanForActivity(player.getContext());
        if (!(act instanceof AppCompatActivity)) {
            return;
        }
        AppCompatActivity appAct = (AppCompatActivity) act;
        BottomSheetDialog dlg = new BottomSheetDialog(appAct);
        View sheet = LayoutInflater.from(appAct).inflate(R.layout.sheet_player_more, null);
        dlg.setContentView(sheet);
        LinearLayout speedBox = sheet.findViewById(R.id.player_speed_container);
        if (speedBox == null) {
            dlg.dismiss();
            return;
        }
        int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                player.getResources().getDisplayMetrics());
        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                player.getResources().getDisplayMetrics());
        TypedValue selectable = new TypedValue();
        appAct.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        for (float sp : speeds) {
            TextView row = new TextView(appAct);
            row.setText(formatSpeedLabel(sp));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.setTextColor(0xFF1A1A1A);
            row.setPadding(padH, padV, padH, padV);
            if (selectable.resourceId != 0) {
                row.setBackgroundResource(selectable.resourceId);
            }
            row.setOnClickListener(v -> {
                try {
                    player.setSpeed(sp, true);
                } catch (Throwable t) {
                    LogUtil.e(t);
                }
                dlg.dismiss();
                ToastUtil.showShort(player.getContext().getString(
                        R.string.player_speed_applied, formatSpeedLabel(sp)));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = padV / 2;
            speedBox.addView(row, lp);
        }
        TextView decoderRow = new TextView(appAct);
        decoderRow.setText(R.string.player_more_decoder_settings);
        decoderRow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        decoderRow.setTextColor(0xFF1A1A1A);
        decoderRow.setPadding(padH, padV, padH, padV);
        if (selectable.resourceId != 0) {
            decoderRow.setBackgroundResource(selectable.resourceId);
        }
        decoderRow.setOnClickListener(v -> {
            dlg.dismiss();
            player.getContext().startActivity(Routes.setting(player.getContext()));
        });
        speedBox.addView(decoderRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dlg.show();
    }

    static String formatSpeedLabel(float s) {
        if (Math.abs(s - 0.5f) < 0.01f) {
            return "0.5×";
        }
        if (Math.abs(s - 0.75f) < 0.01f) {
            return "0.75×";
        }
        if (Math.abs(s - 1f) < 0.01f) {
            return "1×";
        }
        if (Math.abs(s - 1.25f) < 0.01f) {
            return "1.25×";
        }
        if (Math.abs(s - 1.5f) < 0.01f) {
            return "1.5×";
        }
        if (Math.abs(s - 2f) < 0.01f) {
            return "2×";
        }
        return s + "×";
    }
}
