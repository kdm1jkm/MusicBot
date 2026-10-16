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
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsStreamReader;
import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.playlists.HlsStreamTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubePersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Stable queued identity; every execution obtains its own newly signed stream URL. */
public final class YtDlpAudioTrack extends DelegatedAudioTrack {
    private final YtDlpAudioSourceManager source;

    public YtDlpAudioTrack(AudioTrackInfo info, YtDlpAudioSourceManager source) {
        super(info);
        this.source = source;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        JsonNode metadata;
        try {
            metadata = source.resolvePlayback(trackInfo);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (IOException failure) {
            throw YtDlpAudioSourceManager.extractionFailure(failure, trackInfo.identifier);
        }
        try {
            if ("is_upcoming".equals(metadata.path("live_status").asText())) {
                throw new UnsupportedMedia("Video is not yet playable.");
            }
            if (metadata.path("requested_formats").isArray()) {
                throw new UnsupportedMedia("A merged format is not a single supported stream.");
            }
            JsonNode selected = metadata;
            JsonNode downloads = metadata.path("requested_downloads");
            if (downloads.isArray()) {
                if (downloads.size() != 1) throw new UnsupportedMedia("Multiple media streams are unsupported.");
                selected = downloads.get(0);
            }
            String url = selected.path("url").asText("");
            URI uri = URI.create(url);
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new UnsupportedMedia("No supported media URL was returned.");
            }
            Map<String, String> headers = YtDlpAudioSourceManager.mediaHeaders(metadata, selected);
            String protocol = selected.path("protocol").asText("");
            String extension = selected.path("ext").asText("");
            String codec = selected.path("acodec").asText("");
            if (trackInfo.isStream) {
                String container = selected.path("container").asText("");
                if (!(protocol.equals("m3u8_native") || protocol.equals("m3u8")) || !(codec.isEmpty() || codec.startsWith("mp4a"))
                        || (!container.isEmpty() && !container.equals("mpegts"))
                        || !(extension.equals("mp4") || extension.equals("ts"))) {
                    throw new UnsupportedMedia("Live media is not supported AAC HLS.");
                }
                processDelegate(new HlsStreamTrack(trackInfo, url, source.httpManager(), true) {
                    @Override protected HttpInterface getHttpInterface() { return source.getHttpInterface(headers); }
                    @Override public boolean isSeekable() { return false; }
                    @Override protected void processJoinedStream(LocalAudioTrackExecutor localExecutor, InputStream stream) throws Exception {
                        BufferedInputStream transport = new BufferedInputStream(stream, 65545);
                        transport.mark(65545);
                        boolean mpegTs = transport.read() == 0x47;
                        for (int index = 1; mpegTs && index < 377; index++) {
                            int value = transport.read();
                            if (value < 0 || (index % 188 == 0 && value != 0x47)) mpegTs = false;
                        }
                        transport.reset();
                        if (mpegTs) {
                            super.processJoinedStream(localExecutor, transport);
                        } else {
                            // YouTube audio-only HLS may use ID3-prefixed packed AAC, with no CODECS metadata.
                            if (new AdtsStreamReader(transport).findPacketHeader(65536) == null)
                                throw new UnsupportedMedia("Live HLS segments are neither MPEG-TS nor packed AAC.");
                            transport.reset();
                            processDelegate(new AdtsAudioTrack(trackInfo, transport), localExecutor);
                        }
                    }
                }, executor);
            } else {
                if (!protocol.equals("https") || !(extension.equals("webm") || extension.equals("m4a"))) {
                    throw new UnsupportedMedia("No supported seekable audio format was returned.");
                }
                try (HttpInterface http = source.getHttpInterface(headers)) {
                    long length = contentLength(selected, uri, http);
                    try (YoutubePersistentHttpStream stream = new YoutubePersistentHttpStream(http, uri, length)) {
                        if (extension.equals("webm")) processDelegate(new MatroskaAudioTrack(trackInfo, stream), executor);
                        else processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (UnsupportedMedia failure) {
            throw YtDlpAudioSourceManager.problem("YouTube UNSUPPORTED_MEDIA failure for " + trackInfo.identifier + ": " + failure.getMessage());
        } catch (FriendlyException failure) {
            // Even delegate FriendlyExceptions can contain signed URLs in their nested causes.
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("YouTube playback interrupted.");
            }
            throw YtDlpAudioSourceManager.failure("UNSUPPORTED_MEDIA", trackInfo.identifier);
        } catch (Exception failure) {
            if (Thread.currentThread().isInterrupted()) {
                InterruptedException interrupted = new InterruptedException("YouTube playback interrupted.");
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            throw YtDlpAudioSourceManager.failure(failure instanceof IOException ? "MEDIA_IO" : "UNSUPPORTED_MEDIA", trackInfo.identifier);
        }
    }

    private static long contentLength(JsonNode selected, URI uri, HttpInterface http) throws IOException {
        JsonNode size = selected.path("filesize");
        if (size.isIntegralNumber() && size.canConvertToLong() && size.asLong() > 0) return size.asLong();
        for (var parameter : URLEncodedUtils.parse(uri, StandardCharsets.UTF_8)) {
            if ("clen".equals(parameter.getName())) {
                long value = positiveLong(parameter.getValue());
                if (value > 0) return value;
            }
        }
        try (CloseableHttpResponse response = http.execute(new HttpHead(uri))) {
            // A partial response's length is not the resource's total length.
            if (response.getStatusLine().getStatusCode() == 200) {
                Header header = response.getFirstHeader("Content-Length");
                long value = header == null ? -1 : positiveLong(header.getValue());
                if (value > 0) return value;
            }
        }
        throw new UnsupportedMedia("No valid total length is available for seekable playback.");
    }

    private static long positiveLong(String value) {
        if (value == null || value.isEmpty()) return -1;
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') return -1;
        }
        try { long parsed = Long.parseLong(value); return parsed > 0 ? parsed : -1; }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static final class UnsupportedMedia extends IOException {
        UnsupportedMedia(String message) { super(message); }
    }

    @Override public boolean isSeekable() { return !trackInfo.isStream; }
    @Override public void setPosition(long position) { if (isSeekable()) super.setPosition(position); }
    @Override protected AudioTrack makeShallowClone() { return new YtDlpAudioTrack(trackInfo, source); }
    @Override public AudioSourceManager getSourceManager() { return source; }
}
