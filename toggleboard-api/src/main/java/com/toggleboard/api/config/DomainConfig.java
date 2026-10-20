package com.toggleboard.api.config;

import com.toggleboard.api.domain.FlagEvaluator;
import com.toggleboard.api.domain.StableHashRolloutEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public StableHashRolloutEngine stableHashRolloutEngine() {
        return new StableHashRolloutEngine();
    }

    @Bean
    public FlagEvaluator flagEvaluator(StableHashRolloutEngine rolloutEngine) {
        return new FlagEvaluator(rolloutEngine);
    }
}
