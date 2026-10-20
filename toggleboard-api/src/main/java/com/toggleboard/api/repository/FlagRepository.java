package com.toggleboard.api.repository;

import com.toggleboard.api.domain.Flag;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for flags. The DynamoDB-backed implementation arrives
 * in a later phase; {@code memory.InMemoryFlagRepository} is the only
 * implementation for now.
 */
public interface FlagRepository {

    Flag save(Flag flag);

    Optional<Flag> findById(String id);

    Optional<Flag> findByEnvironmentAndKey(String environmentKey, String flagKey);

    List<Flag> findAllByEnvironment(String environmentKey);

    boolean existsByEnvironmentAndKey(String environmentKey, String flagKey);

    void deleteById(String id);
}
