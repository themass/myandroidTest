package com.openapi.ks.myapp.content.sound;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SoundPlaybackControlsTest {

    private FakePlayback playback;
    private FakePlayIcon playIcon;
    private FakeModeSink modeSink;
    private FakeHost host;
    private SoundPlaybackControls controls;

    @Before
    public void setUp() {
        playback = new FakePlayback();
        playIcon = new FakePlayIcon();
        modeSink = new FakeModeSink();
        host = new FakeHost();
        controls = newControls(playback);
    }

    private SoundPlaybackControls newControls(FakePlayback source) {
        return new SoundPlaybackControls(host, () -> source, playIcon, modeSink);
    }

    // ---- resume / pause / isPlaying ----

    @Test
    public void resume_returnsMinusOne_whenServiceNull() {
        controls = new SoundPlaybackControls(host, () -> null, playIcon, modeSink);
        assertEquals(-1, controls.resume());
        assertEquals(0, playIcon.calls);
    }

    @Test
    public void resume_doesNothing_whenAlreadyPlaying() {
        playback.playing = true;
        assertEquals(-1, controls.resume());
        assertEquals(0, playback.resumeCalls);
        assertEquals(0, playIcon.calls);
    }

    @Test
    public void resume_resumesAndShowsPause_whenPaused() {
        playback.playing = false;
        playback.position = 4200;
        assertEquals(4200, controls.resume());
        assertEquals(1, playback.resumeCalls);
        assertEquals(Boolean.TRUE, playIcon.last);
    }

    @Test
    public void pause_doesNothing_whenNotPlaying() {
        playback.playing = false;
        assertEquals(-1, controls.pause());
        assertEquals(0, playback.pauseCalls);
        assertEquals(0, playIcon.calls);
    }

    @Test
    public void pause_pausesAndShowsPlay_whenPlaying() {
        playback.playing = true;
        playback.position = 1000;
        assertEquals(1000, controls.pause());
        assertEquals(1, playback.pauseCalls);
        assertEquals(Boolean.FALSE, playIcon.last);
    }

    @Test
    public void isPlaying_false_whenServiceNull() {
        controls = new SoundPlaybackControls(host, () -> null, playIcon, modeSink);
        assertFalse(controls.isPlaying());
    }

    // ---- play ----

    @Test
    public void play_showsEmptyList_whenNoItems() {
        host.count = 0;
        controls.play(0);
        assertEquals(1, host.emptyListCalls);
        assertEquals(0, playback.playPosiCalls);
        assertEquals(0, playIcon.calls);
    }

    @Test
    public void play_clampsNegativeToZero() {
        host.count = 3;
        controls.play(-5);
        assertEquals(0, playback.lastPlayPosi);
    }

    @Test
    public void play_clampsPastEndToLast() {
        host.count = 3;
        controls.play(9);
        assertEquals(2, playback.lastPlayPosi);
    }

    @Test
    public void play_selectsZero_whenRequestedZeroOrNegative() {
        host.count = 3;
        controls.play(-1);
        assertEquals(1, host.selectedCount(0));
    }

    @Test
    public void play_doesNotSelectZero_whenClampedFromPastEnd() {
        // 与现有代码一致：先判断 position==0 再钳位到末尾，所以越界请求不会选中第 0 项
        host.count = 1;
        controls.play(5);
        assertEquals(0, host.selectedCount(0));
        assertEquals(0, playback.lastPlayPosi);
    }

    @Test
    public void play_showsPauseAndCallsPlayPosi() {
        host.count = 3;
        controls.play(1);
        assertEquals(Boolean.TRUE, playIcon.last);
        assertEquals(1, playback.lastPlayPosi);
    }

    // ---- next / previous ----

    @Test
    public void next_doesNothing_whenServiceNullOrEmpty() {
        host.count = 0;
        controls.next();
        assertEquals(0, playback.playPosiCalls);

        controls = new SoundPlaybackControls(host, () -> null, playIcon, modeSink);
        host.count = 3;
        controls.next();
        assertEquals(0, playback.playPosiCalls);
    }

    @Test
    public void next_playsComputedNextIndex() {
        host.count = 3;
        controls.setCurrent(0);
        playback.nextIndex = 1;
        controls.next();
        assertEquals(0, playback.nextArgCur);
        assertEquals(3, playback.nextArgCount);
        assertEquals(1, playback.lastPlayPosi);
    }

    @Test
    public void pre_playsComputedPreviousIndex() {
        host.count = 3;
        controls.setCurrent(2);
        playback.prevIndex = 1;
        controls.pre();
        assertEquals(2, playback.prevArgCur);
        assertEquals(3, playback.prevArgCount);
        assertEquals(1, playback.lastPlayPosi);
    }

    // ---- togglePlayPause ----

    @Test
    public void togglePlayPause_pausesWhenPlaying() {
        playback.playing = true;
        controls.togglePlayPause();
        assertEquals(1, playback.pauseCalls);
    }

    @Test
    public void togglePlayPause_playsFirstWhenNeverStarted() {
        host.count = 3;
        playback.playing = false;
        controls.togglePlayPause();
        assertEquals(0, playback.lastPlayPosi);
        assertEquals(0, playback.resumeCalls);
    }

    @Test
    public void togglePlayPause_resumesWhenPausedAfterStart() {
        host.count = 3;
        controls.setCurrent(1);
        playback.playing = false;
        controls.togglePlayPause();
        assertEquals(1, playback.resumeCalls);
        assertEquals(0, playback.playPosiCalls);
    }

    // ---- setCurrent / sync / seek / mode ----

    @Test
    public void setCurrent_isReadBack() {
        controls.setCurrent(7);
        assertEquals(7, controls.current());
    }

    @Test
    public void syncButton_noop_whenServiceNull() {
        controls = new SoundPlaybackControls(host, () -> null, playIcon, modeSink);
        controls.syncPlayButtonFromService();
        assertEquals(0, playIcon.calls);
    }

    @Test
    public void syncButton_matchesServiceState() {
        playback.playing = true;
        controls.syncPlayButtonFromService();
        assertEquals(Boolean.TRUE, playIcon.last);
        playback.playing = false;
        controls.syncPlayButtonFromService();
        assertEquals(Boolean.FALSE, playIcon.last);
    }

    @Test
    public void seekTo_callsServiceAndShowsPause() {
        controls.seekTo(5000);
        assertEquals(5000, playback.lastSeek);
        assertEquals(Boolean.TRUE, playIcon.last);
    }

    @Test
    public void applyPlayModeIcon_noop_whenSinkNull() {
        controls = new SoundPlaybackControls(host, () -> playback, playIcon, null);
        controls.applyPlayModeIcon();
        assertEquals(0, modeSink.applies);
    }

    @Test
    public void cyclePlayMode_noop_whenServiceNull() {
        controls = new SoundPlaybackControls(host, () -> null, playIcon, modeSink);
        controls.cyclePlayMode();
        assertEquals(0, modeSink.cycles);
    }

    @Test
    public void cyclePlayMode_cyclesWhenServiceBound() {
        controls.cyclePlayMode();
        assertEquals(1, modeSink.cycles);
        assertTrue(modeSink.applies == 0);
    }

    // ---- fakes ----

    static final class FakePlayback implements SoundPlaybackControls.Playback {
        boolean playing;
        int position;
        int resumeCalls;
        int pauseCalls;
        int playPosiCalls;
        int lastPlayPosi = -99;
        int lastSeek = -99;
        int nextIndex = -1;
        int prevIndex = -1;
        int nextArgCur = -99;
        int nextArgCount = -99;
        int prevArgCur = -99;
        int prevArgCount = -99;

        @Override public boolean isPlaying() { return playing; }
        @Override public int getPosition() { return position; }
        @Override public void resume() { resumeCalls++; playing = true; }
        @Override public void pause() { pauseCalls++; playing = false; }
        @Override public void playPosi(int index) { playPosiCalls++; lastPlayPosi = index; playing = true; }
        @Override public void seekTo(int positionMs) { lastSeek = positionMs; }
        @Override public int computeNextIndex(int cur, int count) { nextArgCur = cur; nextArgCount = count; return nextIndex; }
        @Override public int computePreviousIndex(int cur, int count) { prevArgCur = cur; prevArgCount = count; return prevIndex; }
    }

    static final class FakePlayIcon implements SoundPlaybackControls.PlayIconSink {
        Boolean last;
        int calls;

        @Override public void showPlaying(boolean playing) { last = playing; calls++; }
    }

    static final class FakeModeSink implements SoundPlaybackControls.PlayModeSink {
        int cycles;
        int applies;

        @Override public void cycle() { cycles++; }
        @Override public void apply() { applies++; }
    }

    static final class FakeHost implements SoundPlaybackControls.Host {
        int count;
        int emptyListCalls;
        final java.util.List<Integer> selected = new java.util.ArrayList<>();

        @Override public int itemCount() { return count; }
        @Override public void selectItem(int index) { selected.add(index); }
        @Override public void showEmptyList() { emptyListCalls++; }

        int selectedCount(int index) {
            int n = 0;
            for (int i : selected) {
                if (i == index) n++;
            }
            return n;
        }
    }
}
