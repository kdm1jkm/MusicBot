package com.jagrosh.jmusicbot.config.migration.versions;

import com.jagrosh.jmusicbot.config.migration.Migration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/** Removes the retired yt-dlp cookies setting without touching the referenced file. */
public final class V3ToV4 implements Migration {
    @Override
    public int getFromVersion() {
        return 3;
    }

    @Override
    public int getToVersion() {
        return 4;
    }

    @Override
    public Config migrate(Config source) {
        return source.withoutPath("playback.youtube.cookiesFile")
                .withValue("meta.configVersion", ConfigValueFactory.fromAnyRef(getToVersion()));
    }
}
