package com.openapi.ks.dh.duix.sdk.client;

import android.content.Context;

import java.io.File;

/**
 * Local addition (not upstream): where the vendored SDK looks for {@code model/gj_dh_res},
 * {@code model/<name>} and their {@code model/tmp/*} markers.
 *
 * Upstream hardcodes {@code context.getExternalFilesDir("duix")}. {@code :dh-engines} downloads the
 * packs into its AssetStore cache instead, so {@code DuixRuntime} points this at that directory.
 * Without an override the upstream location is used.
 */
public final class DuixHome {
    private static volatile File override;

    private DuixHome() {}

    public static void setRoot(File root) {
        override = root;
    }

    public static File dir(Context context) {
        File root = override;
        return root != null ? root : context.getExternalFilesDir("duix");
    }
}
