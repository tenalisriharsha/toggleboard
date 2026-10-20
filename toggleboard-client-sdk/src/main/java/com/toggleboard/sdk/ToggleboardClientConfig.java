package com.toggleboard.sdk;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for a {@link ToggleboardClient}. Build with {@link #builder()}.
 */
public final class ToggleboardClientConfig {

    private final String baseUrl;
    private final String environmentKey;
    private final String apiKey;
    private final Duration pollInterval;
    private final boolean streamingEnabled;
    private final Duration httpTimeout;

    private ToggleboardClientConfig(Builder builder) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(builder.baseUrl, "baseUrl is required"));
        this.environmentKey = Objects.requireNonNull(builder.environmentKey, "environmentKey is required");
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey is required");
        this.pollInterval = Objects.requireNonNull(builder.pollInterval, "pollInterval is required");
        this.streamingEnabled = builder.streamingEnabled;
        this.httpTimeout = Objects.requireNonNull(builder.httpTimeout, "httpTimeout is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String environmentKey() {
        return environmentKey;
    }

    public String apiKey() {
        return apiKey;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public boolean streamingEnabled() {
        return streamingEnabled;
    }

    public Duration httpTimeout() {
        return httpTimeout;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class Builder {
        private String baseUrl;
        private String environmentKey;
        private String apiKey;
        private Duration pollInterval = Duration.ofSeconds(30);
        private boolean streamingEnabled = true;
        private Duration httpTimeout = Duration.ofSeconds(5);

        private Builder() {
        }

        /** The toggleboard-api base URL, e.g. {@code http://localhost:8080}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder environmentKey(String environmentKey) {
            this.environmentKey = environmentKey;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** How often the polling fallback re-fetches every cached flag/subject pair. */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /** Whether to subscribe to the server's SSE push channel in addition to polling. */
        public Builder streamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
            return this;
        }

        public Builder httpTimeout(Duration httpTimeout) {
            this.httpTimeout = httpTimeout;
            return this;
        }

        public ToggleboardClientConfig build() {
            return new ToggleboardClientConfig(this);
        }
    }
}
