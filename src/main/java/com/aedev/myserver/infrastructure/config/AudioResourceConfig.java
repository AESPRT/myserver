package com.aedev.myserver.infrastructure.config;

import com.aedev.myserver.infrastructure.audio.AudioProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.util.annotation.NonNull;

@Configuration
public class AudioResourceConfig implements WebMvcConfigurer {

    private final AudioProperties audioProperties;

    public AudioResourceConfig(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String location = audioProperties.storagePath();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/audio/**")
                .addResourceLocations("file:" + location);
    }
}