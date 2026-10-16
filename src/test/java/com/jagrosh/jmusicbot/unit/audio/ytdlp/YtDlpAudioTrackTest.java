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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.jagrosh.jmusicbot.audio.ytdlp.YtDlpAudioSourceManagerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class YtDlpAudioTrackTest {
    private static final String ID = "FCwjUdAp2nk";

    @Test void realWebmFramesApplyPreplaySeekThenSeekAgainAndCloneIndependently() throws Exception {
        byte[] media = WebmTestMedia.create();
        List<String> paths = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            int offset = 0;
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.startsWith("range=")) offset = Integer.parseInt(query.substring(6).split("-", 2)[0]);
            exchange.sendResponseHeaders(200, media.length - offset);
            try (var body = exchange.getResponseBody()) { body.write(media, offset, media.length - offset); }
            finally { exchange.close(); }
        });
        server.start();
        AtomicInteger resolutions = new AtomicInteger();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
            ObjectNode metadata = (ObjectNode) video(ID);
            if (args.contains("--format")) {
                metadata.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/fresh-" + resolutions.incrementAndGet());
                metadata.put("filesize", media.length);
                metadata.put("ext", "webm");
                metadata.put("protocol", "https");
            } else metadata.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/expired");
            return metadata;
        });
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        manager.registerSourceManager(source);
        AudioPlayer player = manager.createPlayer();
        AtomicReference<FriendlyException> error = new AtomicReference<>();
        player.addListener(new AudioEventAdapter() {
            @Override public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) { error.set(exception); }
        });
        try {
            AudioTrack track = (AudioTrack) source.loadItem(manager, new AudioReference(ID, null));
            Object request = new Object();
            track.setUserData(request);
            track.setPosition(10000);
            player.startTrack(track, false);
            assertNotNull(frameIn(player, error, 10000, 11000));
            track.setPosition(60000);
            assertNotNull(frameIn(player, error, 60000, 61000));
            player.stopTrack();
            AudioTrack clone = track.makeClone();
            assertSame(request, clone.getUserData());
            clone.setPosition(20000);
            player.startTrack(clone, false);
            assertNotNull(frameIn(player, error, 20000, 21000));
            assertEquals(2, resolutions.get());
            assertTrue(paths.contains("/fresh-1"));
            assertTrue(paths.contains("/fresh-2"));
            assertFalse(paths.contains("/expired"));
        } finally { player.destroy(); manager.shutdown(); server.stop(0); }
    }

    private static AudioFrame frameIn(AudioPlayer player, AtomicReference<FriendlyException> error, long start, long end) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            assertNull(error.get(), () -> "Unexpected track exception: " + error.get());
            try {
                AudioFrame frame = player.provide(100, TimeUnit.MILLISECONDS);
                if (frame != null && frame.getTimecode() >= start && frame.getTimecode() <= end) return frame;
            } catch (TimeoutException ignored) {
                // Initialization and real HTTP decoding may take more than one polling interval.
            }
        }
        fail("No real decoded frame reached the requested time range " + start + "-" + end);
        return null;
    }

    @Test void actualMediaConsumerUsesFreshUrlAndCloneResolvesAgain() throws Exception {
        List<String> requested = new CopyOnWriteArrayList<>();
        List<Map<String, List<String>>> receivedHeaders = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            receivedHeaders.add(Map.copyOf(exchange.getRequestHeaders()));
            byte[] bytes = WebmTestMedia.unsupportedMp4();
            // Deliberately malformed media: this test proves the real decoder consumed the fresh HTTP
            // resource, not successful audio decoding (covered by the packaged-JAR smoke).
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicInteger playResolutions = new AtomicInteger();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
            ObjectNode metadata = (ObjectNode) video(ID);
            if (args.contains("--format")) {
                metadata.put("url", origin + (playResolutions.incrementAndGet() == 1 ? "/B" : "/C") + "?token=private");
                metadata.put("protocol", "https");
                metadata.put("ext", "m4a");
                metadata.put("filesize", 1024);
                metadata.set("http_headers", json("{\"User-Agent\":\"top-agent\",\"Referer\":\"https://youtube.com/\",\"Cookie\":\"must-not-forward\",\"Authorization\":\"must-not-forward\",\"Host\":\"wrong.invalid\"}"));
                ObjectNode selected = metadata.deepCopy();
                selected.set("http_headers", json("{\"User-Agent\":\"format-agent\",\"Accept-Language\":\"ko\",\"Range\":\"bytes=bad\"}"));
                metadata.putArray("requested_downloads").add(selected);
            } else metadata.put("url", origin + "/A?expired=private");
            return metadata;
        });
        try {
            YtDlpAudioTrack track = (YtDlpAudioTrack) source.loadItem(null, new AudioReference(ID, null));
            FriendlyException first = assertThrows(FriendlyException.class, () -> track.process(null));
            assertNull(first.getCause());
            assertFalse(first.toString().contains("private"));
            assertEquals(List.of("GET /B"), requested);
            assertEquals("format-agent", receivedHeaders.getFirst().get("User-agent").getFirst());
            assertEquals("https://youtube.com/", receivedHeaders.getFirst().get("Referer").getFirst());
            assertFalse(receivedHeaders.getFirst().containsKey("Cookie"));
            assertFalse(receivedHeaders.getFirst().containsKey("Authorization"));
            assertFalse(receivedHeaders.getFirst().containsKey("Range"));
            YtDlpAudioTrack clone = (YtDlpAudioTrack) track.makeClone();
            assertThrows(FriendlyException.class, () -> clone.process(null));
            assertEquals(List.of("GET /B", "GET /C"), requested);
            assertEquals(2, playResolutions.get());
        } finally { source.shutdown(); server.stop(0); }
    }

    @Test void headersAreWhitelistedOverlaidAndClearedWhenInterfaceIsReturned() throws Exception {
        List<Map<String, List<String>>> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.add(Map.copyOf(exchange.getRequestHeaders()));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> video(ID));
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        try {
            Map<String, String> headers = YtDlpAudioSourceManager.mediaHeaders(
                    json("{\"http_headers\":{\"User-Agent\":\"top\",\"Origin\":\"https://youtube.com\",\"Cookie\":\"private\"}}"),
                    json("{\"http_headers\":{\"user-agent\":\"selected\",\"Host\":\"bad\",\"Range\":\"bad\",\"Accept\":\"bad\\r\\nheader\"}}"));
            assertEquals(Map.of("user-agent", "selected", "origin", "https://youtube.com"), headers);
            try (var http = source.getHttpInterface(headers); var response = http.execute(new HttpGet(url))) {
                assertEquals(204, response.getStatusLine().getStatusCode());
            }
            try (var http = source.getHttpInterface(Map.of()); var response = http.execute(new HttpGet(url))) {
                assertEquals(204, response.getStatusLine().getStatusCode());
                assertNull(http.getContext().getAttribute("unrelated"));
            }
            assertEquals("selected", seen.getFirst().get("User-agent").getFirst());
            assertFalse(seen.getLast().containsKey("Origin"));
            assertNotEquals("selected", seen.getLast().get("User-agent").getFirst());
        } finally { source.shutdown(); server.stop(0); }
    }

    @Test void missingTotalLengthRejectsPartialHeadAndApproximation() throws Exception {
        AtomicInteger gets = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestMethod().equals("GET")) gets.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Length", "100");
            exchange.sendResponseHeaders(206, -1);
            exchange.close();
        });
        server.start();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
            ObjectNode metadata = (ObjectNode) video(ID);
            metadata.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/?clen=0");
            metadata.put("protocol", "https");
            metadata.put("ext", "m4a");
            metadata.put("filesize_approx", 999999);
            return metadata;
        });
        try {
            var track = (YtDlpAudioTrack) source.loadItem(null, new AudioReference(ID, null));
            FriendlyException failure = assertThrows(FriendlyException.class, () -> track.process(null));
            assertTrue(failure.getMessage().contains("No valid total length"));
            assertEquals(0, gets.get());
        } finally { source.shutdown(); server.stop(0); }
    }

    @Test void livePackedAacWithMissingCodecMetadataDecodesAndRefusesSeek() throws Exception {
        // AAC-LC stereo silence, 48 kHz: one 1024-sample frame, repeated behind an empty ID3 tag.
        byte[] packet = java.util.HexFormat.of().parseHex("fff14c8001bffc211004608c1c");
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.write(new byte[]{'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0});
        for (int index = 0; index < 100; index++) bytes.write(packet);
        byte[] media = bytes.toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().endsWith(".m3u8")
                    ? "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:3\n#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:3,\nsegment.aac\n#EXT-X-ENDLIST\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : media;
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> json("{\"id\":\"" + ID
                + "\",\"is_live\":true,\"url\":\"http://127.0.0.1:" + server.getAddress().getPort()
                + "/live.m3u8\",\"protocol\":\"m3u8_native\",\"ext\":\"mp4\"}"));
        var manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        manager.registerSourceManager(source);
        AudioPlayer player = manager.createPlayer();
        AtomicReference<FriendlyException> error = new AtomicReference<>();
        player.addListener(new AudioEventAdapter() {
            @Override public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) { error.set(exception); }
        });
        try {
            AudioTrack track = (AudioTrack) source.loadItem(manager, new AudioReference(ID, null));
            assertFalse(track.isSeekable());
            track.setPosition(60000);
            assertEquals(0, track.getPosition());
            player.playTrack(track);
            AudioFrame frame = frameIn(player, error, 1000, 1100);
            assertEquals(3840, frame.getDataLength());
            for (byte sample : frame.getData()) assertEquals(0, sample, "Silent AAC must decode to silent PCM.");
            assertFalse(track.isSeekable());
        } finally { player.destroy(); manager.shutdown(); server.stop(0); }
    }

    @Test void liveRefusesSeekingAndUnsupportedHlsContainerFailsBeforeNetwork() throws Exception {
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> json("{\"id\":\"" + ID + "\",\"is_live\":true,\"url\":\"https://invalid.invalid/stream\",\"protocol\":\"m3u8_native\",\"ext\":\"mp4\",\"acodec\":\"mp4a.40.2\",\"container\":\"fmp4\"}"));
        try {
            var track = (YtDlpAudioTrack) source.loadItem(null, new AudioReference(ID, null));
            assertFalse(track.isSeekable());
            track.setPosition(60000);
            assertEquals(0, track.getPosition());
            assertThrows(FriendlyException.class, () -> track.process(null));
        } finally { source.shutdown(); }
    }

    @Test void hlsManifestAndSegmentConsumeTrackHeadersAndRejectActualFmp4Bytes() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(path);
            headers.add(Map.copyOf(exchange.getRequestHeaders()));
            byte[] body = path.endsWith(".m3u8")
                    ? "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:1,\nsegment.mp4\n#EXT-X-ENDLIST\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : new byte[400];
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> json("{\"id\":\"" + ID
                + "\",\"is_live\":true,\"url\":\"http://127.0.0.1:" + server.getAddress().getPort()
                + "/live.m3u8\",\"protocol\":\"m3u8_native\",\"ext\":\"mp4\",\"acodec\":\"mp4a.40.2\","
                + "\"http_headers\":{\"User-Agent\":\"live-agent\",\"Referer\":\"https://youtube.com/\",\"Cookie\":\"private\"}}"));
        try {
            var track = (YtDlpAudioTrack) source.loadItem(null, new AudioReference(ID, null));
            assertThrows(FriendlyException.class, () -> track.process(null));
            assertTrue(requests.contains("/segment.mp4"), "The real media consumer must request the HLS segment.");
            for (var requestHeaders : headers) {
                assertEquals("live-agent", requestHeaders.get("User-agent").getFirst());
                assertEquals("https://youtube.com/", requestHeaders.get("Referer").getFirst());
                assertFalse(requestHeaders.containsKey("Cookie"));
            }
        } finally { source.shutdown(); server.stop(0); }
    }

    @Test void positiveClenAndHeadLengthBothPermitRealMediaConsumption() throws Exception {
        List<String> methods = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            methods.add(exchange.getRequestMethod());
            exchange.getResponseHeaders().set("Content-Length", "1024");
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } else {
                exchange.sendResponseHeaders(200, 1024);
                try (var output = exchange.getResponseBody()) { output.write(WebmTestMedia.unsupportedMp4()); }
                finally { exchange.close(); }
            }
        });
        server.start();
        try {
            for (String query : List.of("?clen=1024", "")) {
                var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
                    ObjectNode metadata = (ObjectNode) video(ID);
                    metadata.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/" + query);
                    metadata.put("protocol", "https");
                    metadata.put("ext", "m4a");
                    return metadata;
                });
                try {
                    var track = (YtDlpAudioTrack) source.loadItem(null, new AudioReference(ID, null));
                    assertThrows(FriendlyException.class, () -> track.process(null));
                } finally { source.shutdown(); }
            }
            assertEquals(List.of("GET", "HEAD", "GET"), methods);
        } finally { server.stop(0); }
    }

    @Test void playbackStopInterruptsPendingExtractionAndShutdownRejectsFurtherExtraction() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean flagPreserved = new AtomicBoolean();
        var source = new YtDlpAudioSourceManager(1, "", null, (args, timeout) -> {
            if (!args.contains("--format")) return video(ID);
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                flagPreserved.set(Thread.currentThread().isInterrupted());
                interrupted.countDown();
                throw failure;
            }
            throw new AssertionError("Pending extraction unexpectedly resumed");
        });
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(source);
        AudioPlayer player = manager.createPlayer();
        try {
            AudioTrack track = (AudioTrack) source.loadItem(manager, new AudioReference(ID, null));
            player.startTrack(track, false);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            player.stopTrack();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertTrue(flagPreserved.get());
            source.shutdown();
            assertThrows(FriendlyException.class, () -> source.loadItem(manager, new AudioReference(ID, null)));
        } finally { player.destroy(); manager.shutdown(); }
    }
}
