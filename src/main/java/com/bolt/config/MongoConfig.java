package com.bolt.config;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig
{
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeouts()
    {
        return builder -> builder
            .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(2, TimeUnit.SECONDS))
            .applyToSocketSettings(socket -> socket
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS));
    }
}
