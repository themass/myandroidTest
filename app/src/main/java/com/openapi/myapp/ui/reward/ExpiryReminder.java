package com.openapi.myapp.ui.reward;

import android.app.Activity;
import android.app.AlertDialog;

import com.openapi.common.util.PreferenceUtils;
import com.openapi.ks.free1.R;
import com.openapi.myapp.bean.vo.RewardStatusVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.yewu.net.request.CommonResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 观看券与 VIP 即将到期提醒。
 * 登录状态下回到首页时检查一次，每天最多提醒一次。
 * 满足以下任一条件才提醒：24 小时内有观看券过期；VIP 在 24 小时内到期。
 */
public final class ExpiryReminder {

    private static final String KEY_REMIND_DAY = "EXPIRY_REMIND_DAY";
    private static final String TAG = "EXPIRY_REMIND_TAG";
    private static final long WINDOW_MS = 24L * 3600_000L;
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    private ExpiryReminder() {
    }

    public static void check(final Activity activity, BaseService service) {
        if (UserLoginUtil.getUserCache() == null) {
            return;
        }
        final String today = dayFormat().format(new Date());
        if (today.equals(PreferenceUtils.getPrefString(activity, KEY_REMIND_DAY, ""))) {
            return;
        }
        service.getData(Constants.getUrl(Constants.API_REWARD_STATUS_URL),
                new CommonResponse.ResponseOkListener<RewardStatusVo>() {
                    @Override
                    public void onResponse(RewardStatusVo vo) {
                        super.onResponse(vo);
                        if (vo == null || activity.isFinishing()) {
                            return;
                        }
                        List<String> lines = buildLines(activity, vo);
                        if (lines.isEmpty()) {
                            return;
                        }
                        PreferenceUtils.setPrefString(activity, KEY_REMIND_DAY, today);
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.expiry_title)
                                .setMessage(join(lines))
                                .setPositiveButton(R.string.expiry_use, (d, w) -> RewardActivity.start(activity))
                                .setNegativeButton(R.string.expiry_ok, null)
                                .show();
                    }
                },
                null, TAG, RewardStatusVo.class);
    }

    private static List<String> buildLines(Activity activity, RewardStatusVo vo) {
        List<String> lines = new ArrayList<>();
        if (vo.expiringTickets > 0) {
            lines.add(activity.getString(R.string.expiry_tickets, vo.expiringTickets));
        }
        Date end = parseVipEnd(vo.vipEnd);
        long now = System.currentTimeMillis();
        if (end != null && end.getTime() > now && end.getTime() - now <= WINDOW_MS) {
            lines.add(activity.getString(R.string.expiry_vip, vo.vipEnd));
        }
        return lines;
    }

    private static Date parseVipEnd(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        fmt.setTimeZone(ZONE);
        try {
            return fmt.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private static SimpleDateFormat dayFormat() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        fmt.setTimeZone(ZONE);
        return fmt;
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }
}
