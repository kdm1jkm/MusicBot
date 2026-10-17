package com.jagrosh.jmusicbot.unit.config.migration.versions;

import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.versions.V3ToV4;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class V3ToV4Test {
    @TempDir
    Path tempDir;

    @Test
    void removesOnlyCookiesSettingAndPreservesReferencedFile() throws IOException {
        Path cookies = tempDir.resolve("youtube-cookies.txt");
        String cookieContent = "# Netscape HTTP Cookie File\n";
        Files.writeString(cookies, cookieContent);
        Config source = ConfigFactory.parseString("""
                meta.configVersion = 3
                discord.token = "test-token"
                playback.maxYouTubePlaylistPages = 20
                playback.audioSources.youtube = false
                playback.youtube.customOption = "retained"
                custom.values = [1, 2, 3]
                """).withValue("playback.youtube.cookiesFile",
                com.typesafe.config.ConfigValueFactory.fromAnyRef(cookies.toString()));

        Config migrated = new V3ToV4().migrate(source);

        assertEquals(4, migrated.getInt("meta.configVersion"));
        assertFalse(migrated.hasPathOrNull("playback.youtube.cookiesFile"));
        assertEquals(source.withoutPath("playback.youtube.cookiesFile").withoutPath("meta.configVersion"),
                migrated.withoutPath("meta.configVersion"));
        assertEquals(cookies.toString(), source.getString("playback.youtube.cookiesFile"));
        assertEquals(cookieContent, Files.readString(cookies));
    }

    @Test
    void migratesVersionThreeWithoutCookies() {
        Config migrated = new V3ToV4().migrate(ConfigFactory.parseString("""
                meta.configVersion = 3
                playback.maxYouTubePlaylistPages = 5
                """));

        assertEquals(4, migrated.getInt("meta.configVersion"));
        assertFalse(migrated.hasPathOrNull("playback.youtube.cookiesFile"));
        assertEquals(5, migrated.getInt("playback.maxYouTubePlaylistPages"));
    }

    @Test
    void legacyMigrationReachesVersionFourWithoutRetiredYouTubeSettings() {
        Config migrated = ConfigMigration.migrate(ConfigFactory.parseString("""
                token = "test-token"
                owner = 123456789
                prefix = "!!"
                useyoutubeoauth = true
                playback.youtube.cookiesFile = "obsolete.txt"
                """), 0, 4);

        assertEquals(4, migrated.getInt("meta.configVersion"));
        assertFalse(migrated.hasPathOrNull("playback.youtube.cookiesFile"));
        assertFalse(migrated.hasPath("playback.youtube.useOAuth"));
        assertFalse(migrated.hasPath("useyoutubeoauth"));
        assertEquals("test-token", migrated.getString("discord.token"));
        assertEquals(123456789L, migrated.getLong("discord.owner"));
        assertEquals("!!", migrated.getString("commands.prefix"));
    }
}
