package com.openapi.feature.video.subtitle.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain-text subset of SRT and WebVTT. Positions are media time, not wall time. */
final class LocalSubtitleTrack {
    private static final Pattern TIMING = Pattern.compile(
            "((?:\\d{1,6}:)?[0-5]\\d:[0-5]\\d[,.]\\d{3})\\s+-->\\s+"
                    + "((?:\\d{1,6}:)?[0-5]\\d:[0-5]\\d[,.]\\d{3})(?:\\s+.*)?");
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final int MAX_CHARS = 1_048_576;
    private static final int MAX_CUES = 10_000;

    private record Cue(long start, long end, String text) {}

    private final List<Cue> cues;
    private final long[] maxEnd;

    private LocalSubtitleTrack(List<Cue> cues) {
        this.cues = cues;
        maxEnd = new long[cues.size()];
        long end = 0;
        for (int i = 0; i < cues.size(); i++) {
            end = Math.max(end, cues.get(i).end());
            maxEnd[i] = end;
        }
    }

    static LocalSubtitleTrack read(Reader reader) throws IOException {
        // Bound input before readLine can allocate a potentially enormous single line.
        StringBuilder document = new StringBuilder();
        char[] buffer = new char[4096];
        for (int n; (n = reader.read(buffer)) != -1; ) {
            if (Thread.currentThread().isInterrupted() || document.length() + n > MAX_CHARS) {
                throw new IOException("Subtitle import interrupted or too large");
            }
            document.append(buffer, 0, n);
        }
        List<Cue> cues = new ArrayList<>();
        try (BufferedReader lines = new BufferedReader(new java.io.StringReader(document.toString()))) {
            List<String> block = new ArrayList<>();
            for (String line; (line = lines.readLine()) != null; ) {
                if (line.startsWith("﻿")) line = line.substring(1);
                if (line.trim().isEmpty()) {
                    addBlock(block, cues);
                    block.clear();
                } else {
                    block.add(line);
                }
            }
            addBlock(block, cues);
        }
        if (cues.isEmpty()) throw new IOException("No supported subtitle cues");
        cues.sort(Comparator.comparingLong(Cue::start));
        return new LocalSubtitleTrack(cues);
    }

    private static void addBlock(List<String> block, List<Cue> cues) throws IOException {
        if (block.size() < 2) return;
        String first = block.get(0).trim();
        if (first.startsWith("WEBVTT") || first.equals("STYLE") || first.equals("REGION")
                || first.equals("NOTE") || first.startsWith("NOTE ") || first.startsWith("NOTE\t")) return;
        int timingIndex = first.contains("-->") ? 0 : 1;
        Matcher timing = TIMING.matcher(block.get(timingIndex).trim());
        if (!timing.matches() || timingIndex + 1 >= block.size()) return;
        long start = millis(timing.group(1));
        long end = millis(timing.group(2));
        if (end <= start) return;
        String text = TAG.matcher(String.join("\n", block.subList(timingIndex + 1, block.size())))
                .replaceAll("").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").replace("&amp;", "&").trim();
        if (text.isEmpty()) return;
        if (cues.size() >= MAX_CUES) throw new IOException("Too many subtitle cues");
        // This prototype renders at most a few lines of plain text.
        cues.add(new Cue(start, end, text.length() > 500 ? text.substring(0, 500) : text));
    }

    private static long millis(String time) {
        String[] parts = time.replace(',', '.').split("[:.]");
        int n = parts.length;
        long hours = n == 4 ? Long.parseLong(parts[0]) : 0;
        return ((hours * 60 + Long.parseLong(parts[n - 3])) * 60
                + Long.parseLong(parts[n - 2])) * 1000 + Long.parseLong(parts[n - 1]);
    }

    String at(long positionMs, int delayMs) {
        long time = positionMs - delayMs;
        int first = 0;
        int last = cues.size();
        while (first < last) {
            int mid = (first + last) / 2;
            if (maxEnd[mid] <= time) first = mid + 1;
            else last = mid;
        }
        StringBuilder result = new StringBuilder();
        int active = 0;
        for (int i = first; i < cues.size() && active < 3; i++) {
            Cue cue = cues.get(i);
            if (cue.start() > time) break;
            if (cue.end() > time) {
                if (active++ != 0) result.append('\n');
                result.append(cue.text());
            }
        }
        return result.toString();
    }
}
