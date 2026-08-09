package com.openapi.ks.auxio.musicui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun AuxioMusicRemoteRoot(
    apiRoot: String,
    channel: String,
    keyword: String,
    playlistTitle: String,
    volleyTag: String,
    onFinish: () -> Unit,
) {
    val activity = LocalContext.current as ComponentActivity
    val app = activity.application
    val loadVm: PlaylistLoadViewModel = viewModel(
        factory = remember(apiRoot, channel, keyword, volleyTag) {
            PlaylistLoadViewModel.Factory(app, apiRoot, channel, keyword, volleyTag)
        },
    )
    val ui by loadVm.uiState.collectAsState()
    val refreshing by loadVm.refreshing.collectAsState()
    val loadingMore by loadVm.loadingMore.collectAsState()

    LaunchedEffect(Unit) {
        loadVm.loadInitial()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (val s = ui) {
            is PlaylistLoadUiState.Idle,
            is PlaylistLoadUiState.Loading,
            -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PlaylistLoadUiState.Error -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { loadVm.loadInitial() }) {
                        Text(stringResource(R.string.auxio_list_retry))
                    }
                }
            }
            is PlaylistLoadUiState.Ready -> {
                val playerVm: PlayerViewModel = viewModel(
                    key = s.sessionKey.toString(),
                    factory = PlayerViewModel.Factory(app, s.initialTracks),
                )
                val hasMoreNow = (ui as? PlaylistLoadUiState.Ready)?.hasMore == true
                AuxioMusicNavHost(
                    playlistTitle = playlistTitle,
                    playerVm = playerVm,
                    onFinish = onFinish,
                    remoteListExtras = AuxioRemoteListExtras(
                        onRefresh = {
                            loadVm.refreshAll { fresh -> playerVm.replacePlaylist(fresh) }
                        },
                        refreshing = refreshing,
                        onLoadMore = {
                            loadVm.loadMoreAppend(playerVm.nextTrackIdBase()) { more ->
                                playerVm.appendToPlaylist(more)
                            }
                        },
                        loadingMore = loadingMore,
                        hasMore = hasMoreNow,
                    ),
                )
            }
        }
    }
}
