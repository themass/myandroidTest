package com.openapi.ks.myapp.translation.api;

import androidx.annotation.NonNull;

public interface RealtimeSubtitleCallbacks {

    void onLoading();

    void onPartialText(@NonNull String text);

    void onError(@NonNull String message);

    void onStopped();
}
