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
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.typesafe.config.ConfigFactory;
import com.sun.net.httpserver.HttpServer;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PlayerManager Tests")
class PlayerManagerTest {

    @Mock
    private Bot bot;

    @Mock
    private BotConfig config;

    private PlayerManager playerManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(bot.getConfig()).thenReturn(config);
        
        // Default config mock setup
        when(config.getFrameBufferMs()).thenReturn(2000);
        when(config.getTransforms()).thenReturn(ConfigFactory.empty());
        when(config.getEnabledAudioSources()).thenReturn(Set.of()); // Empty to avoid source registration
        
        playerManager = new PlayerManager(bot);
    }

    @Nested
    @DisplayName("Proxy Configuration Tests")
    class ProxyConfigurationTests {


        @Test
        void initRoutesYoutubeHttpRequestsThroughEnabledProxy() throws Exception {
            assertYoutubeHttpRoute(true, true, "proxy");
        }

        @Test
        void initKeepsYoutubeHttpRequestsDirectWhenProxyDisabled() throws Exception {
            assertYoutubeHttpRoute(false, true, "origin");
        }

        @Test
        void initKeepsYoutubeHttpRequestsDirectWhenProxyMissing() throws Exception {
            assertYoutubeHttpRoute(true, false, "origin");
        }

        private void assertYoutubeHttpRoute(boolean enabled, boolean configured, String expected) throws Exception {
            HttpServer origin = routeServer("origin");
            HttpServer proxy = routeServer("proxy");
            try {
                when(config.proxyLavaplayer()).thenReturn(enabled);
                when(config.hasProxy()).thenReturn(configured);
                when(config.getProxyHost()).thenReturn("127.0.0.1");
                when(config.getProxyPort()).thenReturn(proxy.getAddress().getPort());
                when(config.getMaxYTPlaylistPages()).thenReturn(1);
                when(config.getEnabledAudioSources()).thenReturn(Set.of(AudioSource.YOUTUBE));

                playerManager.init();

                YoutubeAudioSourceManager youtube = playerManager.source(YoutubeAudioSourceManager.class);
                String url = "http://127.0.0.1:" + origin.getAddress().getPort() + "/source-request";
                try (var http = youtube.getInterface(); var response = http.execute(new HttpGet(url))) {
                    assertEquals(expected, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                }
            } finally {
                playerManager.shutdown();
                proxy.stop(0);
                origin.stop(0);
            }
        }

        private HttpServer routeServer(String route) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = route.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        }
    }

}
