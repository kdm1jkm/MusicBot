/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.audio.ytdlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class YtDlpAudioSourceManagerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ID = "FCwjUdAp2nk";
    private static final String SECOND = "abcdefghijk";

    static JsonNode json(String value) throws IOException { return JSON.readTree(value); }
    static JsonNode video(String id) throws IOException {
        return json("{\"id\":\"" + id + "\",\"title\":\"윤하 사건의 지평선\",\"channel\":\"윤하\",\"duration\":120.25,\"live_status\":\"not_live\",\"url\":\"https://secret.invalid/expired?token=secret\"}");
    }
    private static AudioItem load(YtDlpAudioSourceManager source, String input) { return source.loadItem(null, new AudioReference(input, null)); }

    @Test void unrelatedAndBlankInputsNeverInvokeExtraction() {
        AtomicInteger calls = new AtomicInteger();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> { calls.incrementAndGet(); return video(ID); });
        try {
            for (String value : List.of("", " ", "https://youtube.com.evil.invalid/watch?v=" + ID,
                    "https://notyoutube.com/watch?v=" + ID, "https://example.com/youtube.com/watch?v=" + ID,
                    "https://youtube.com@evil.invalid/watch?v=" + ID)) assertNull(load(source, value));
            assertNull(source.loadItem(null, null));
            assertSame(AudioReference.NO_TRACK, load(source, "ytsearch:  "));
            assertEquals(0, calls.get());
        } finally { source.shutdown(); }
    }

    @Test void recognizedMalformedUrlsFailRatherThanFallingThrough() {
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> { fail("Malformed input invoked extraction"); return null; });
        try {
            for (String value : List.of("https://youtube.com/watch?v=bad", "https://youtube.com/watch?v=with space",
                    "https://youtu.be/bad", "https://youtube.com/playlist?list=bad", "https://youtube.com/channel/abc",
                    "https://youtube.com/watch?v=" + ID + "&list=bad", "https://name@youtube.com/watch?v=" + ID)) {
                assertThrows(FriendlyException.class, () -> load(source, value), value);
            }
        } finally { source.shutdown(); }
    }

    @Test void supportedUrlsCanonicalizeAndPreserveKoreanStableMetadata() {
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> video(ID));
        try {
            for (String input : List.of(ID, "http://m.youtube.com/watch?v=" + ID + "&t=60", "https://youtu.be/" + ID,
                    "https://www.youtube.com/shorts/" + ID, "https://youtube.com/live/" + ID,
                    "https://www.youtube-nocookie.com/embed/" + ID)) {
                AudioTrack track = (AudioTrack) load(source, input);
                assertEquals("윤하 사건의 지평선", track.getInfo().title);
                assertEquals("윤하", track.getInfo().author);
                assertEquals(120250, track.getDuration());
                assertEquals(YtDlpAudioSourceManager.publicUrl(ID), track.getInfo().uri);
                assertTrue(track.isSeekable());
            }
        } finally { source.shutdown(); }
    }

    @Test void playlistOrderSelectionUnavailableAndUnknownDurationArePreserved() throws Exception {
        JsonNode entries = json("{\"title\":\"재생 목록\",\"entries\":[null,{\"id\":\"" + ID + "\",\"title\":\"flat\"},"
                + video(SECOND) + ",{\"id\":\"unavailable\",\"availability\":\"private\"}]}");
        var source = new YtDlpAudioSourceManager(3, "", null, (args, timeout) ->
                args.contains("--flat-playlist") ? entries : video(ID));
        try {
            AudioPlaylist playlist = (AudioPlaylist) load(source, "https://youtube.com/watch?v=" + SECOND + "&list=PLexample");
            assertEquals("재생 목록", playlist.getName());
            assertFalse(playlist.isSearchResult());
            assertEquals(List.of(ID, SECOND), playlist.getTracks().stream().map(AudioTrack::getIdentifier).toList());
            assertSame(playlist.getTracks().get(1), playlist.getSelectedTrack());
            assertEquals(120250, playlist.getTracks().getFirst().getDuration());
        } finally { source.shutdown(); }
    }

    @Test void optionLookingSearchRemainsOneDataOperandAndEmptyResultsAreNoMatch() throws Exception {
        List<List<String>> calls = new ArrayList<>();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
            calls.add(args);
            return json("{\"entries\":[" + video(ID) + "]}");
        });
        try {
            AudioPlaylist result = (AudioPlaylist) load(source, "ytsearch:--exec malicious 윤하");
            assertTrue(result.isSearchResult());
            assertEquals(List.of("--flat-playlist", "--", "ytsearch20:--exec malicious 윤하"), calls.getFirst());
        } finally { source.shutdown(); }
        var empty = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> json("{\"entries\":[]}"));
        try { assertSame(AudioReference.NO_TRACK, load(empty, "ytsearch:missing")); } finally { empty.shutdown(); }
    }

    @Test void liveIsNonSeekableUpcomingFailsAndUnresolvableFlatEntriesSkip() throws Exception {
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> json("{\"id\":\"" + ID + "\",\"is_live\":true}"));
        try {
            AudioTrack track = (AudioTrack) load(source, ID);
            assertTrue(track.getInfo().isStream);
            assertFalse(track.isSeekable());
            track.setPosition(60000);
            assertEquals(0, track.getPosition());
        } finally { source.shutdown(); }
        var upcoming = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> json("{\"id\":\"" + ID + "\",\"live_status\":\"is_upcoming\"}"));
        try { assertTrue(assertThrows(FriendlyException.class, () -> load(upcoming, ID)).getMessage().contains("not yet playable")); }
        finally { upcoming.shutdown(); }
        var unresolved = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
            if (args.contains("--flat-playlist")) return json("{\"entries\":[{\"id\":\"" + ID + "\"}]}");
            throw new IOException("Unavailable fixture");
        });
        try { assertSame(AudioReference.NO_TRACK, load(unresolved, "ytsearch:removed")); } finally { unresolved.shutdown(); }
    }

    @Test void failedEnumerationIsAnErrorAndSerializationContainsOnlyVersion() throws Exception {
        var broken = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> { throw new IOException("Fixture failed"); });
        try { assertThrows(FriendlyException.class, () -> load(broken, "ytsearch:윤하")); } finally { broken.shutdown(); }
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> video(ID));
        try {
            AudioTrack original = (AudioTrack) load(source, ID);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            source.encodeTrack(original, new DataOutputStream(bytes));
            assertArrayEquals(new byte[]{1}, bytes.toByteArray());
            AudioTrack decoded = source.decodeTrack(original.getInfo(), new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertEquals(original.getInfo().title, decoded.getInfo().title);
            assertEquals(original.getIdentifier(), decoded.getIdentifier());
            assertEquals(YtDlpAudioSourceManager.publicUrl(ID), decoded.getInfo().uri);
            assertThrows(IOException.class, () -> source.decodeTrack(original.getInfo(), new DataInputStream(new ByteArrayInputStream(new byte[]{2}))));
            Object request = new Object();
            original.setUserData(request);
            original.setPosition(60000);
            assertEquals(60000, original.getPosition());
            AudioTrack clone = original.makeClone();
            assertNotSame(original, clone);
            assertSame(request, clone.getUserData());
            assertSame(source, clone.getSourceManager());
        } finally { source.shutdown(); }
    }

    @Test void invalidConfiguredCookiePathIsNeverAnonymous(@TempDir Path directory) {
        assertThrows(FriendlyException.class, () -> new YtDlpAudioSourceManager(1, directory.resolve("missing.txt").toString(), null));
        assertThrows(FriendlyException.class, () -> new YtDlpAudioSourceManager(1, directory.toString(), null));
    }
}
