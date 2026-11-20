package com.openapi.ks.dh.duix.sdk.client;

/**
 * Hand-authored shim replacing the AGP-generated {@code BuildConfig} of the upstream Duix SDK
 * ({@code ai.guiji.duix.sdk.client.BuildConfig}). The vendored sources only read
 * {@link #VERSION_CODE}, {@link #VERSION_NAME} and {@link #DEBUG}; {@code :dh-engines} does not
 * enable AGP buildConfig generation.
 */
public final class BuildConfig {
    private BuildConfig() {}

    public static final int VERSION_CODE = 1;
    public static final String VERSION_NAME = "2.0.1-lab";
    public static final boolean DEBUG = false;
}
