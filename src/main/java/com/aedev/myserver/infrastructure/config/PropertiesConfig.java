package com.aedev.myserver.infrastructure.config;

import com.aedev.myserver.infrastructure.payment.paymongo.PayMongoProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * Enables @ConfigurationProperties record binding across the
 * infrastructure package. Using scan rather than @EnableConfigurationProperties
 * per-class keeps this file stable as more properties classes are added.
 */
@Configuration
@ConfigurationPropertiesScan(basePackageClasses = {
        PayMongoProperties.class,
        FrontendProperties.class
})
public class PropertiesConfig {
}