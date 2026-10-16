package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.audio.TransformativeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TransformativeAudioSourceManagerTest {
    @Test
    void returnsReferenceForRegisteredSourcesAndRejectsSelfRecursion() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<span>FCwjUdAp2nk</span>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            var source = new TransformativeAudioSourceManager("fixture", "input", url, "span", "https://www.youtube.com/watch?v=%s");
            AudioReference result = assertInstanceOf(AudioReference.class,
                    source.loadItem(null, new AudioReference("input", null)));
            assertEquals("https://www.youtube.com/watch?v=FCwjUdAp2nk", result.identifier);
            assertNull(source.loadItem(null, result));
            var self = new TransformativeAudioSourceManager("self", "FCwjUdAp2nk", url, "span", "%s");
            assertNull(self.loadItem(null, new AudioReference("FCwjUdAp2nk", null)));
            assertFalse(source.isTrackEncodable(null));
            assertNull(source.decodeTrack(null, null));
            assertThrows(UnsupportedOperationException.class, () -> source.encodeTrack(null, null));
        } finally {
            server.stop(0);
        }
    }
}
