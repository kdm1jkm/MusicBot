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
package com.jagrosh.jmusicbot.audio;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.AndroidWithThumbnail;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.IosWithThumbnail;
import dev.lavalink.youtube.clients.MusicWithThumbnail;
import dev.lavalink.youtube.clients.WebEmbeddedWithThumbnail;
import dev.lavalink.youtube.clients.WebWithThumbnail;
import org.apache.http.HttpHost;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.ProxyUtil;

/**
 * Enum representing available audio sources that can be listed in the config file.
 * 
 * <p><b>Registration Order Matters:</b> Sources are registered with the audio player manager
 * in order of their {@link #getRegistrationPriority() registrationPriority}. When a URL is
 * played, sources are checked in registration order until one claims the URL. This means:
 * <ul>
 *   <li>Platform-specific sources (YouTube, SoundCloud, etc.) should have LOW priority numbers
 *       so they are registered FIRST and get first chance to claim their URLs</li>
 *   <li>Catch-all sources (HTTP, LOCAL) should have HIGH priority numbers so they are
 *       registered LAST and only claim URLs that no specific source wanted</li>
 * </ul>
 * 
 * <p>If HTTP is registered before SoundCloud, it will claim SoundCloud URLs and fail to play them.
 * 
 * @author Arif Banai (arif-banai)
 */
public enum AudioSource
{
    // Platform-specific sources (priority 10-90) - registered first to claim their URLs
    YOUTUBE(
        "youtube",
        "YouTube videos and playlists",
        10,
        (manager, config) -> {
            HttpHost proxy = config.proxyLavaplayer() && config.hasProxy()
                    ? ProxyUtil.createApacheProxy(config) : null;
            WebEmbeddedWithThumbnail embedded = proxy == null
                    ? new WebEmbeddedWithThumbnail() : new ProxiedWebEmbeddedClient(proxy);
            // Android VR still loads playlists, but its playback endpoint can require login.
            ClientOptions metadataOnly = new ClientOptions();
            metadataOnly.setPlayback(false);
            YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(
                    new MusicWithThumbnail(), new AndroidVrWithThumbnail(metadataOnly),
                    new AndroidWithThumbnail(), new IosWithThumbnail(),
                    new WebWithThumbnail(), embedded);
            youtube.setPlaylistPageCount(config.getMaxYTPlaylistPages());
            if (proxy != null) {
                youtube.getHttpInterfaceManager().configureBuilder(builder -> builder.setProxy(proxy));
            }
            manager.registerSourceManager(youtube);
        }
    ),
    SOUNDCLOUD(
        "soundcloud",
        "SoundCloud tracks",
        20,
        (manager, config) -> manager.registerSourceManager(SoundCloudAudioSourceManager.createDefault())
    ),
    BANDCAMP(
        "bandcamp",
        "Bandcamp albums and tracks",
        30,
        (manager, config) -> manager.registerSourceManager(new BandcampAudioSourceManager())
    ),
    VIMEO(
        "vimeo",
        "Vimeo videos",
        40,
        (manager, config) -> manager.registerSourceManager(new VimeoAudioSourceManager())
    ),
    TWITCH(
        "twitch",
        "Twitch streams",
        50,
        (manager, config) -> manager.registerSourceManager(new TwitchStreamAudioSourceManager())
    ),
    BEAM(
        "beam",
        "Beam.pro streams",
        60,
        (manager, config) -> manager.registerSourceManager(new BeamAudioSourceManager())
    ),
    GETYARN(
        "getyarn",
        "Getyarn.io clips",
        70,
        (manager, config) -> manager.registerSourceManager(new GetyarnAudioSourceManager())
    ),
    NICO(
        "nico",
        "NicoNico videos",
        80,
        (manager, config) -> manager.registerSourceManager(new NicoAudioSourceManager())
    ),
    
    // Catch-all sources (priority 100+) - registered last as fallbacks
    HTTP(
        "http",
        "Direct HTTP audio links",
        100,
        (manager, config) -> manager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY))
    ),
    LOCAL(
        "local",
        "Local file playback",
        110,
        (manager, config) -> AudioSourceManagers.registerLocalSource(manager)
    );

    private final String configName;
    private final String description;
    private final int registrationPriority;
    private final BiConsumer<DefaultAudioPlayerManager, BotConfig> registrationAction;

    AudioSource(String configName, String description, int registrationPriority, 
                BiConsumer<DefaultAudioPlayerManager, BotConfig> registrationAction)
    {
        this.configName = configName;
        this.description = description;
        this.registrationPriority = registrationPriority;
        this.registrationAction = registrationAction;
    }

    /**
     * Gets the configuration name for this audio source (used in config files).
     * 
     * @return the lowercase configuration name
     */
    public String getConfigName()
    {
        return configName;
    }

    /**
     * Gets a human-readable description of this audio source.
     * 
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }
    
    /**
     * Gets the registration priority for this audio source.
     * Lower numbers are registered first. Platform-specific sources should have
     * low priorities (10-90), while catch-all sources like HTTP should have
     * high priorities (100+).
     * 
     * @return the registration priority
     */
    public int getRegistrationPriority()
    {
        return registrationPriority;
    }
    
    /**
     * Returns all audio sources sorted by registration priority (lowest first).
     * This ensures platform-specific sources are registered before catch-all sources.
     * 
     * @return list of audio sources in registration order
     */
    public static java.util.List<AudioSource> valuesSortedByPriority()
    {
        return Arrays.stream(values())
                .sorted(java.util.Comparator.comparingInt(AudioSource::getRegistrationPriority))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Parses an audio source from its configuration name.
     * 
     * @param configName the configuration name (case-insensitive)
     * @return an Optional containing the AudioSource if found, empty otherwise
     */
    public static Optional<AudioSource> fromConfigName(String configName)
    {
        if(configName == null)
            return Optional.empty();
        
        return Arrays.stream(values())
                .filter(source -> source.configName.equalsIgnoreCase(configName.trim()))
                .findFirst();
    }
    
    /**
     * Registers this audio source with the given player manager.
     * 
     * @param manager the player manager to register with
     * @param config the bot configuration
     */
    public void register(DefaultAudioPlayerManager manager, BotConfig config)
    {
        registrationAction.accept(manager, config);
    }
    
}
