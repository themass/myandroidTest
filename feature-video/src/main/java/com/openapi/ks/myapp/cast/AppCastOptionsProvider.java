package com.openapi.ks.myapp.cast;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;

import java.util.List;

/**
 * Google Cast 发送端配置。默认使用系统「Default Media Receiver」（CC1AD845），
 * 上线可改为在 <a href="https://cast.google.com/publish">Cast SDK Console</a> 注册的 Receiver ID。
 */
public class AppCastOptionsProvider implements OptionsProvider {

    @NonNull
    @Override
    public CastOptions getCastOptions(@NonNull Context appContext) {
        return new CastOptions.Builder()
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .build();
    }

    @Nullable
    @Override
    public List<SessionProvider> getAdditionalSessionProviders(@NonNull Context context) {
        return null;
    }
}
