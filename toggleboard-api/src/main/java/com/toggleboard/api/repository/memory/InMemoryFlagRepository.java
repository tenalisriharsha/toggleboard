package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.repository.FlagRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory {@link FlagRepository}. Stands in for the DynamoDB
 * implementation until Phase 2; swapping it out only requires a new
 * implementation of this interface, nothing in the service layer changes.
 */
@Repository
@Profile("!dynamodb")
public class InMemoryFlagRepository implements FlagRepository {

    private final Map<String, Flag> byId = new ConcurrentHashMap<>();

    @Override
    public Flag save(Flag flag) {
        byId.put(flag.id(), flag);
        return flag;
    }

    @Override
    public Optional<Flag> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Flag> findByEnvironmentAndKey(String environmentKey, String flagKey) {
        return byId.values().stream()
                .filter(f -> f.environmentKey().equals(environmentKey) && f.key().equals(flagKey))
                .findFirst();
    }

    @Override
    public List<Flag> findAllByEnvironment(String environmentKey) {
        return byId.values().stream()
                .filter(f -> f.environmentKey().equals(environmentKey))
                .sorted((a, b) -> a.key().compareTo(b.key()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByEnvironmentAndKey(String environmentKey, String flagKey) {
        return findByEnvironmentAndKey(environmentKey, flagKey).isPresent();
    }

    @Override
    public void deleteById(String id) {
        byId.remove(id);
    }
}
