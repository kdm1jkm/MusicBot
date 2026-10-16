package com.jagrosh.jmusicbot.config.migration.versions;

import com.jagrosh.jmusicbot.config.migration.Migration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/** Removes retired YouTube OAuth settings without changing locally supplied cookies. */
public final class V2ToV3 implements Migration {
    @Override
    public int getFromVersion() {
        return 2;
    }

    @Override
    public int getToVersion() {
        return 3;
    }

    @Override
    public Config migrate(Config source) {
        return source.withoutPath("playback.youtube.useOAuth")
                .withoutPath("useyoutubeoauth")
                .withValue("meta.configVersion", ConfigValueFactory.fromAnyRef(getToVersion()));
    }
}
