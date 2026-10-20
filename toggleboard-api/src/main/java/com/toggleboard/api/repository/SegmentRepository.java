package com.toggleboard.api.repository;

import com.toggleboard.api.domain.Segment;

import java.util.List;
import java.util.Optional;

public interface SegmentRepository {

    Segment save(Segment segment);

    Optional<Segment> findById(String id);

    Optional<Segment> findByEnvironmentAndKey(String environmentKey, String key);

    List<Segment> findAllByEnvironment(String environmentKey);

    boolean existsByEnvironmentAndKey(String environmentKey, String key);

    void deleteById(String id);
}
