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
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/** YouTube metadata and fresh playback resolution, using only the bundled tools. */
public final class YtDlpAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final Logger log = LoggerFactory.getLogger(YtDlpAudioSourceManager.class);
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PLAYLIST_ID = Pattern.compile("(?:PL|UU|LL|RD|FL|OLAK5uy_)[A-Za-z0-9_-]*");
    private static final String HEADER_ATTRIBUTE = "jmusicbot.ytdlp.headers";
    private static final Set<String> ALLOWED_HEADERS = Set.of("user-agent", "referer", "origin", "accept", "accept-language");
    private static final Set<String> UNAVAILABLE = Set.of("private", "premium_only", "subscriber_only", "needs_auth");
    static final Duration VIDEO_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ENUMERATION_TIMEOUT = Duration.ofSeconds(120);
    private final long playlistLimit;
    private final String cookiesFile;
    private final HttpHost proxy;
    private final ThreadLocalHttpInterfaceManager http;
    private final Extraction extraction;
    private BundledYtDlpRuntime runtime;
    private YtDlpProcessRunner runner;
    private volatile boolean closed;

    @FunctionalInterface
    interface Extraction {
        JsonNode run(List<String> arguments, Duration timeout) throws IOException, InterruptedException;
    }

    public YtDlpAudioSourceManager(int playlistPages, String cookiesFile, HttpHost proxy) {
        this(playlistPages, cookiesFile, proxy, null);
    }

    // Deterministic metadata tests replace the process boundary, not the media consumer.
    YtDlpAudioSourceManager(int playlistPages, String cookiesFile, HttpHost proxy, Extraction extraction) {
        this.playlistLimit = 100L * Math.max(1, playlistPages);
        this.cookiesFile = cookiesFile == null ? "" : cookiesFile.trim();
        this.proxy = proxy;
        this.extraction = extraction;
        if (!this.cookiesFile.isEmpty()) {
            Path path;
            try { path = OtherUtil.getPath(this.cookiesFile); }
            catch (java.nio.file.InvalidPathException e) { throw problem("Configured YouTube cookies file path is invalid."); }
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw problem("Configured YouTube cookies file must be a readable regular file.");
            }
        }
        http = new ThreadLocalHttpInterfaceManager(HttpClientTools.createSharedCookiesHttpBuilder(), HttpClientTools.DEFAULT_REQUEST_CONFIG);
        http.configureBuilder(builder -> builder.setProxy(proxy).disableAutomaticRetries());
        http.setHttpContextFilter(new MediaHeaders());
    }

    @Override public String getSourceName() { return "youtube"; }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String input = reference == null ? null : reference.identifier;
        Request request = recognize(input);
        if (request == null) return null;
        if (request.operand().isEmpty()) return AudioReference.NO_TRACK;
        try {
            if (request.kind() == Kind.VIDEO) {
                return track(singleMetadata(request.operand()), false);
            }
            List<String> arguments = new ArrayList<>(List.of("--flat-playlist"));
            if (request.kind() == Kind.PLAYLIST) {
                arguments.addAll(List.of("--yes-playlist", "--playlist-end", Long.toString(playlistLimit)));
            }
            arguments.addAll(List.of("--", request.operand()));
            JsonNode result = extract(arguments, ENUMERATION_TIMEOUT);
            JsonNode entries = result.path("entries");
            if (!entries.isArray()) throw problem("YouTube enumeration returned invalid metadata.");
            List<AudioTrack> tracks = new ArrayList<>();
            AudioTrack selected = null;
            int skipped = 0;
            for (JsonNode entry : entries) {
                if (request.kind() == Kind.PLAYLIST && tracks.size() >= playlistLimit) break;
                try {
                    AudioTrack track = track(entry, true);
                    tracks.add(track);
                    if (track.getIdentifier().equals(request.selectedId())) selected = track;
                } catch (FriendlyException | IOException unavailable) {
                    skipped++;
                }
            }
            if (skipped > 0) log.debug("Skipped {} unavailable YouTube entries.", skipped);
            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
            return new BasicAudioPlaylist(text(result, "title", request.kind() == Kind.SEARCH ? "YouTube search" : "YouTube playlist"),
                    tracks, selected, request.kind() == Kind.SEARCH);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("INTERRUPTED", request.selectedId());
        } catch (IOException e) {
            throw extractionFailure(e, request.selectedId());
        }
    }

    private JsonNode singleMetadata(String url) throws IOException, InterruptedException {
        return extract(List.of("--no-playlist", "--", url), VIDEO_TIMEOUT);
    }

    private AudioTrack track(JsonNode metadata, boolean flat) throws IOException, InterruptedException {
        String id = metadata == null ? "" : metadata.path("id").asText("");
        if (!VIDEO_ID.matcher(id).matches()) throw problem("YouTube entry is unavailable or has no valid video ID.");
        String availability = metadata.path("availability").asText("");
        if (UNAVAILABLE.contains(availability)) {
            throw problem("YouTube video " + id + " is unavailable.");
        }
        String status = metadata.path("live_status").asText("");
        if ("is_upcoming".equals(status)) throw problem("YouTube video " + id + " is not yet playable.");
        boolean live = metadata.path("is_live").asBoolean(false) || "is_live".equals(status);
        boolean classified = metadata.path("is_live").isBoolean() || !status.isEmpty();
        JsonNode duration = metadata.path("duration");
        boolean validDuration = duration.isNumber() && Double.isFinite(duration.asDouble()) && duration.asDouble() >= 0;
        if (flat && (!classified || (!live && !validDuration))) return track(singleMetadata(publicUrl(id)), false);
        if (!live && !validDuration) throw problem("YouTube video " + id + " has no playable duration.");
        long length = live ? Units.DURATION_MS_UNKNOWN : Math.round(duration.asDouble() * 1000.0);
        return new YtDlpAudioTrack(new AudioTrackInfo(text(metadata, "title", MediaContainerDetection.UNKNOWN_TITLE),
                text(metadata, "uploader", text(metadata, "channel", MediaContainerDetection.UNKNOWN_ARTIST)), length, id,
                live, publicUrl(id), text(metadata, "thumbnail", "https://img.youtube.com/vi/" + id + "/mqdefault.jpg"), null), this);
    }

    JsonNode resolvePlayback(AudioTrackInfo info) throws IOException, InterruptedException {
        String format = info.isStream
                ? "bestaudio[protocol=m3u8_native][acodec^=?mp4a]/bestaudio[protocol=m3u8][acodec^=?mp4a]/best[protocol=m3u8_native][acodec^=mp4a]/best[protocol=m3u8][acodec^=mp4a]"
                : "bestaudio[ext=webm][protocol=https]/bestaudio[ext=m4a][protocol=https]";
        return extract(List.of("--no-playlist", "--check-formats", "--format", format, "--", publicUrl(info.identifier)), VIDEO_TIMEOUT);
    }

    private JsonNode extract(List<String> arguments, Duration timeout) throws IOException, InterruptedException {
        if (closed) throw new IOException("YouTube source is closed.");
        if (extraction != null) return extraction.run(arguments, timeout);
        return processRunner().run(arguments, timeout);
    }

    private synchronized YtDlpProcessRunner processRunner() throws IOException {
        if (closed) throw new IOException("YouTube source is closed.");
        if (runner == null) {
            BundledYtDlpRuntime initialized = BundledYtDlpRuntime.open();
            try {
                runner = new YtDlpProcessRunner(initialized, cookiesFile, proxy);
                runtime = initialized;
            } catch (IOException | RuntimeException e) {
                initialized.close();
                throw e;
            }
        }
        return runner;
    }

    HttpInterface getHttpInterface(Map<String, String> headers) {
        if (closed) throw problem("YouTube source is closed.");
        HttpInterface result = http.getInterface();
        result.getContext().setAttribute(HEADER_ATTRIBUTE, headers);
        return result;
    }

    ThreadLocalHttpInterfaceManager httpManager() { return http; }

    static Map<String, String> mediaHeaders(JsonNode root, JsonNode selected) {
        Map<String, String> headers = new LinkedHashMap<>();
        copyHeaders(root.path("http_headers"), headers);
        copyHeaders(selected.path("http_headers"), headers);
        return Map.copyOf(headers);
    }

    private static void copyHeaders(JsonNode node, Map<String, String> headers) {
        node.properties().forEach(entry -> {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue().asText("");
            if (ALLOWED_HEADERS.contains(name) && !value.contains("\r") && !value.contains("\n")) headers.put(name, value);
        });
    }

    @Override public void configureRequests(Function<RequestConfig, RequestConfig> configurator) { http.configureRequests(configurator); }
    @Override public void configureBuilder(Consumer<HttpClientBuilder> configurator) { http.configureBuilder(configurator); }
    @Override public boolean isTrackEncodable(AudioTrack track) { return track instanceof YtDlpAudioTrack && track.getSourceManager() == this; }
    @Override public void encodeTrack(AudioTrack track, DataOutput output) throws IOException { output.writeByte(1); }
    @Override public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) throws IOException {
        int version = input.readUnsignedByte();
        if (version != 1) throw new IOException("Unsupported bundled YouTube track format version: " + version);
        if (!VIDEO_ID.matcher(info.identifier).matches()) throw new IOException("Invalid serialized YouTube video ID.");
        return new YtDlpAudioTrack(new AudioTrackInfo(info.title, info.author, info.length, info.identifier, info.isStream,
                publicUrl(info.identifier), info.artworkUrl, info.isrc), this);
    }
    @Override public synchronized void shutdown() {
        closed = true;
        if (runner != null) runner.close();
        if (runtime != null) runtime.close();
        try { http.close(); } catch (IOException e) { log.warn("Could not close YouTube HTTP manager."); }
    }

    static String publicUrl(String id) { return "https://www.youtube.com/watch?v=" + id; }
    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }
    static FriendlyException problem(String message) { return new FriendlyException(message, FriendlyException.Severity.COMMON, null); }
    static FriendlyException extractionFailure(IOException error, String id) {
        if (error instanceof YtDlpProcessRunner.Failure failure) {
            if (failure.category() == YtDlpProcessRunner.Category.AUTHENTICATION) {
                return problem("YouTube requires authentication or rejected this IP. Configure playback.youtube.cookiesFile with a valid YouTube cookies file."
                        + " (AUTHENTICATION" + (id == null ? "" : "; video " + id) + ")");
            }
            return problem("YouTube " + failure.category() + " failure" + (id == null ? "" : " for " + id) + ": " + failure.getMessage());
        }
        // Runtime initialization diagnostics are already safe and include actionable local-file context.
        return problem("YouTube initialization or I/O failure" + (id == null ? "" : " for " + id) + ": " + error.getMessage());
    }
    static FriendlyException failure(String category, String id) {
        return problem("YouTube " + category + " failure" + (id == null ? "" : " for " + id) + ".");
    }

    private enum Kind { VIDEO, PLAYLIST, SEARCH }
    private record Request(Kind kind, String operand, String selectedId) { }

    private static Request recognize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String input = raw.trim();
        if (input.startsWith("ytsearch:")) {
            String query = input.substring(9).trim();
            return new Request(Kind.SEARCH, query.isEmpty() ? "" : "ytsearch20:" + query, null);
        }
        if (VIDEO_ID.matcher(input).matches()) return new Request(Kind.VIDEO, publicUrl(input), input);
        if (PLAYLIST_ID.matcher(input).matches()) return playlist(input, null);
        URI uri;
        try { uri = URI.create(input); }
        catch (IllegalArgumentException e) {
            // Parse the authority separately so a malformed YouTube URL cannot fall through to HTTP.
            int authorityStart = input.indexOf("://");
            if (authorityStart >= 0) {
                String authority = input.substring(authorityStart + 3).split("[/\\?#]", 2)[0];
                try {
                    String host = URI.create("https://" + authority).getHost();
                    if (youtubeHost(host)) throw problem("Malformed YouTube URL.");
                } catch (IllegalArgumentException ignored) { }
            }
            return null;
        }
        String host = uri.getHost();
        if (!youtubeHost(host)) return null;
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null) {
            throw problem("Malformed YouTube URL.");
        }
        Map<String, String> query = new HashMap<>();
        try { URLEncodedUtils.parse(uri, StandardCharsets.UTF_8).forEach(p -> query.putIfAbsent(p.getName(), p.getValue())); }
        catch (IllegalArgumentException e) { throw problem("Malformed YouTube URL query."); }
        String path = uri.getPath();
        String video = query.get("v");
        if (host.equalsIgnoreCase("youtu.be") || host.toLowerCase(Locale.ROOT).endsWith(".youtu.be")) {
            video = path != null && path.startsWith("/") ? path.substring(1) : "";
        } else if (path != null && (path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/"))) {
            video = path.substring(path.indexOf('/', 1) + 1);
        } else if (!"/watch".equals(path) && !"/playlist".equals(path)) {
            throw problem("Unsupported YouTube URL path.");
        }
        String list = query.get("list");
        if (list != null) {
            if (!PLAYLIST_ID.matcher(list).matches() || (video != null && !VIDEO_ID.matcher(video).matches())) throw problem("Malformed YouTube playlist URL.");
            return playlist(list, video);
        }
        if (video == null || !VIDEO_ID.matcher(video).matches()) throw problem("Malformed YouTube video URL.");
        return new Request(Kind.VIDEO, publicUrl(video), video);
    }

    private static Request playlist(String id, String selected) {
        return new Request(Kind.PLAYLIST, "https://www.youtube.com/playlist?list=" + id, selected);
    }
    private static boolean youtubeHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("youtube.com") || lower.endsWith(".youtube.com")
                || lower.equals("youtu.be") || lower.endsWith(".youtu.be")
                || lower.equals("youtube-nocookie.com") || lower.endsWith(".youtube-nocookie.com");
    }

    private static final class MediaHeaders implements HttpContextFilter {
        @Override public void onContextOpen(HttpClientContext context) { }
        @Override public void onContextClose(HttpClientContext context) { context.removeAttribute(HEADER_ATTRIBUTE); }
        @Override public void onRequest(HttpClientContext context, HttpUriRequest request, boolean repeated) {
            Object value = context.getAttribute(HEADER_ATTRIBUTE);
            if (value instanceof Map<?, ?> headers) headers.forEach((name, header) -> {
                if (name instanceof String key && header instanceof String text
                        && ALLOWED_HEADERS.contains(key.toLowerCase(Locale.ROOT))
                        && !text.contains("\r") && !text.contains("\n")) request.setHeader(key, text);
            });
        }
        @Override public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) { return false; }
        @Override public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) { return false; }
    }
}
