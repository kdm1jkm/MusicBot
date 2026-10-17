package com.jagrosh.jmusicbot.audio;

import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxiedWebEmbeddedClientTest {
    @Test
    @Timeout(10)
    void embeddedHostFlagsRequestUsesConfiguredProxyTunnel() throws Exception {
        try (var proxy = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            proxy.setSoTimeout(3000);
            var request = executor.submit(() -> {
                try (var socket = proxy.accept()) {
                    socket.setSoTimeout(3000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String firstLine = reader.readLine();
                    for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
                        // Consume headers before returning a deterministic proxy refusal.
                    }
                    socket.getOutputStream().write(("HTTP/1.1 502 Bad Gateway\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    return firstLine;
                }
            });
            var client = new ProxiedWebEmbeddedClient(new HttpHost("127.0.0.1", proxy.getLocalPort()));
            assertNull(client.fetchEncryptedHostFlags("abcdefghijk"));
            assertEquals("CONNECT www.youtube.com:443 HTTP/1.1", request.get(4, TimeUnit.SECONDS));
        }
    }
}
