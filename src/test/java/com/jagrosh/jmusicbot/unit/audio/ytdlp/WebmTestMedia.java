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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** A real 90-second WebM/Opus silence stream, generated without an external encoder. */
final class WebmTestMedia {
    private WebmTestMedia() { }

    static byte[] unsupportedMp4() {
        // One complete MP4 free box: syntactically finite, but deliberately has no audio tracks.
        return ByteBuffer.allocate(1024).putInt(1024).put("free".getBytes(StandardCharsets.US_ASCII)).array();
    }

    static byte[] create() {
        byte[] ebml = element(0x1A45DFA3, element(0x4282, "webm".getBytes(StandardCharsets.US_ASCII)));
        byte[] info = element(0x1549A966, join(element(0x2AD7B1, integer(1_000_000)),
                element(0x4489, ByteBuffer.allocate(8).putDouble(90_000).array())));
        byte[] opusHead = ByteBuffer.allocate(19).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put("OpusHead".getBytes(StandardCharsets.US_ASCII)).put((byte) 1).put((byte) 2)
                .putShort((short) 0).putInt(48_000).putShort((short) 0).put((byte) 0).array();
        byte[] track = element(0x1654AE6B, element(0xAE, join(element(0xD7, new byte[]{1}),
                element(0x73C5, new byte[]{1}), element(0x83, new byte[]{2}),
                element(0x86, "A_OPUS".getBytes(StandardCharsets.US_ASCII)), element(0x63A2, opusHead),
                element(0xE1, join(element(0xB5, ByteBuffer.allocate(8).putDouble(48_000).array()),
                        element(0x9F, new byte[]{2}))))));
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        segment.writeBytes(info);
        segment.writeBytes(track);
        for (int second = 0; second < 90; second++) {
            ByteArrayOutputStream cluster = new ByteArrayOutputStream();
            cluster.writeBytes(element(0xE7, integer(second * 1000L)));
            for (int millis = 0; millis < 1000; millis += 20) {
                // RFC 7845's conventional valid 20 ms stereo Opus silence packet.
                byte[] block = ByteBuffer.allocate(7).put((byte) 0x81).putShort((short) millis)
                        .put((byte) 0x80).put(new byte[]{(byte) 0xF8, (byte) 0xFF, (byte) 0xFE}).array();
                cluster.writeBytes(element(0xA3, block));
            }
            segment.writeBytes(element(0x1F43B675, cluster.toByteArray()));
        }
        return join(ebml, element(0x18538067, segment.toByteArray()));
    }

    private static byte[] integer(long value) { return ByteBuffer.allocate(8).putLong(value).array(); }
    private static byte[] element(long id, byte[] payload) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int idLength = id > 0xffffff ? 4 : id > 0xffff ? 3 : id > 0xff ? 2 : 1;
        for (int shift = (idLength - 1) * 8; shift >= 0; shift -= 8) bytes.write((int) (id >>> shift));
        int sizeLength = 1;
        while (payload.length >= (1L << (7 * sizeLength)) - 1) sizeLength++;
        long size = payload.length | (1L << (7 * sizeLength));
        for (int shift = (sizeLength - 1) * 8; shift >= 0; shift -= 8) bytes.write((int) (size >>> shift));
        bytes.writeBytes(payload);
        return bytes.toByteArray();
    }
    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] part : parts) bytes.writeBytes(part);
        return bytes.toByteArray();
    }
}
