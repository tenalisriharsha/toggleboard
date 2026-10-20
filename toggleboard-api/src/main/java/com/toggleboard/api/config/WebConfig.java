package com.toggleboard.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Angular admin console's dev server (a different origin than
 * this API) to call the admin CRUD and data-plane endpoints directly from
 * the browser. There is no cookie-based session here — every call carries
 * its own {@code X-Role}/{@code X-Actor}/{@code X-API-Key} header — so
 * credentials are left disabled and the allowed origins are the handful of
 * local dev-server ports Angular CLI uses.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Role", "X-Actor", "X-API-Key");
    }
}
