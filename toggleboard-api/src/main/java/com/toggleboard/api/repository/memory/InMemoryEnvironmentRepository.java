package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.repository.EnvironmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!dynamodb")
public class InMemoryEnvironmentRepository implements EnvironmentRepository {

    private final Map<String, Environment> byKey = new ConcurrentHashMap<>();

    @Override
    public Environment save(Environment environment) {
        byKey.put(environment.key(), environment);
        return environment;
    }

    @Override
    public Optional<Environment> findByKey(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    @Override
    public Optional<Environment> findByApiKey(String apiKey) {
        return byKey.values().stream()
                .filter(e -> e.apiKey().equals(apiKey))
                .findFirst();
    }

    @Override
    public List<Environment> findAll() {
        return List.copyOf(byKey.values());
    }

    @Override
    public boolean existsByKey(String key) {
        return byKey.containsKey(key);
    }
}
