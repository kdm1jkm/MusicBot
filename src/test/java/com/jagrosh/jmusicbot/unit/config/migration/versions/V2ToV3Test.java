package com.jagrosh.jmusicbot.unit.config.migration.versions;

import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.versions.V2ToV3;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class V2ToV3Test {
    @Test
    void removesOAuthAndPreservesCookiesAndUnrelatedSettings() {
        Config source = ConfigFactory.parseString("""
                meta.configVersion = 2
                playback.youtube.useOAuth = true
                useyoutubeoauth = true
                playback.youtube.cookiesFile = "cookies/local.txt"
                custom.setting = "retained"
                """);
        Config migrated = new V2ToV3().migrate(source);
        assertEquals(3, migrated.getInt("meta.configVersion"));
        assertFalse(migrated.hasPath("playback.youtube.useOAuth"));
        assertFalse(migrated.hasPath("useyoutubeoauth"));
        assertEquals("cookies/local.txt", migrated.getString("playback.youtube.cookiesFile"));
        assertEquals("retained", migrated.getString("custom.setting"));
        assertTrue(source.getBoolean("playback.youtube.useOAuth"));
    }

    @Test
    void legacyConfigurationReachesVersionThreeWithoutOAuth() {
        Config migrated = ConfigMigration.migrate(ConfigFactory.parseString("""
                token = "test-token"
                owner = 123456789
                useyoutubeoauth = true
                prefix = "!!"
                """), 0, 3);
        assertEquals(3, migrated.getInt("meta.configVersion"));
        assertFalse(migrated.hasPath("playback.youtube.useOAuth"));
        assertFalse(migrated.hasPath("useyoutubeoauth"));
        assertEquals("!!", migrated.getString("commands.prefix"));
        assertEquals("test-token", migrated.getString("discord.token"));
    }
}
