package com.openapi.ks.auxio.musicui

internal data class AuxioRemoteListExtras(
    val onRefresh: () -> Unit,
    val refreshing: Boolean,
    val onLoadMore: () -> Unit,
    val loadingMore: Boolean,
    val hasMore: Boolean,
)
