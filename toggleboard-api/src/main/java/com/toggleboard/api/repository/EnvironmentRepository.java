package com.toggleboard.api.repository;

import com.toggleboard.api.domain.Environment;

import java.util.List;
import java.util.Optional;

public interface EnvironmentRepository {

    Environment save(Environment environment);

    Optional<Environment> findByKey(String key);

    Optional<Environment> findByApiKey(String apiKey);

    List<Environment> findAll();

    boolean existsByKey(String key);
}
