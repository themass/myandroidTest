package com.openapi.subtitle.api;

import androidx.annotation.NonNull;

public interface RealtimeSubtitleCallbacks {

    void onLoading();

    void onPartialText(@NonNull String text);

    void onError(@NonNull String message);

    void onStopped();
}
