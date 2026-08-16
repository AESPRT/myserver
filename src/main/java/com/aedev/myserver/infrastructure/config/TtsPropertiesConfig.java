package com.aedev.myserver.infrastructure.config;

import com.aedev.myserver.infrastructure.audio.AudioProperties;
import com.aedev.myserver.infrastructure.security.AppApiKeyProperties;
import com.aedev.myserver.infrastructure.tts.ElevenLabsProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan(basePackageClasses = {
        ElevenLabsProperties.class,
        AudioProperties.class,
        AppApiKeyProperties.class
})
public class TtsPropertiesConfig {
}