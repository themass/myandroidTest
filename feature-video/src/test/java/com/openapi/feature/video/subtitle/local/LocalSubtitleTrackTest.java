package com.openapi.feature.video.subtitle.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;

public class LocalSubtitleTrackTest {
    @Test public void srtUsesMediaTimeAndSignedDelay() throws IOException {
        LocalSubtitleTrack track = read("﻿1\r\n00:00:01,000 --> 00:00:03,000\r\n你好");
        assertEquals("", track.at(1499, 500));
        assertEquals("你好", track.at(1500, 500));
        assertEquals("", track.at(3500, 500));
        assertEquals("你好", track.at(500, -500));
        assertEquals("", track.at(0, 0));
    }

    @Test public void vttSkipsNotesAndKeepsOverlappingCues() throws IOException {
        LocalSubtitleTrack track = read("WEBVTT\n\nNOTE ignore\n00:00.000 --> 00:05.000\nno\n\n"
                + "id\n00:01.000 --> 00:03.000 align:start\n<i>first</i>\n\n"
                + "00:02.000 --> 00:04.000\nsecond &amp; last");
        assertEquals("", track.at(999, 0));
        assertEquals("first\nsecond & last", track.at(2000, 0));
        assertEquals("second & last", track.at(3000, 0));
        assertEquals("first", track.at(1200, 0));
    }

    @Test public void rejectsInvalidAndOversizedDocuments() {
        assertThrows(IOException.class, () -> read("not a subtitle"));
        assertThrows(IOException.class, () -> read("x".repeat(1_048_577)));
    }

    private static LocalSubtitleTrack read(String text) throws IOException {
        return LocalSubtitleTrack.read(new StringReader(text));
    }
}
