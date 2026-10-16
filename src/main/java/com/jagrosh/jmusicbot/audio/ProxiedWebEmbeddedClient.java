package com.jagrosh.jmusicbot.audio;

import dev.lavalink.youtube.clients.WebEmbeddedWithThumbnail;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Keeps youtube-source 1.18.2's separate embed-page request on the configured audio proxy. */
final class ProxiedWebEmbeddedClient extends WebEmbeddedWithThumbnail {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxiedWebEmbeddedClient.class);
    private static final Pattern HOST_FLAGS = Pattern.compile("\"encryptedHostFlags\":\"([^\"]+)\"");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final HttpHost proxy;

    ProxiedWebEmbeddedClient(HttpHost proxy) {
        this.proxy = proxy;
    }

    @Override
    protected String fetchEncryptedHostFlags(String videoId) {
        // Upstream creates a fresh default client here, bypassing its source HTTP manager.
        try (var client = HttpClients.custom().setProxy(proxy).build()) {
            HttpGet request = new HttpGet("https://www.youtube.com/embed/" + videoId);
            request.setHeader("User-Agent", USER_AGENT);
            try (var response = client.execute(request)) {
                String html = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                var matcher = HOST_FLAGS.matcher(html);
                return matcher.find() ? matcher.group(1) : null;
            }
        } catch (IOException error) {
            LOGGER.debug("Failed to fetch encryptedHostFlags for video {}", videoId, error);
            return null;
        }
    }
}
