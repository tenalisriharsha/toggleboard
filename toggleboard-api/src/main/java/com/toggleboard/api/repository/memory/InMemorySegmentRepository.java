package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.repository.SegmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Profile("!dynamodb")
public class InMemorySegmentRepository implements SegmentRepository {

    private final Map<String, Segment> byId = new ConcurrentHashMap<>();

    @Override
    public Segment save(Segment segment) {
        byId.put(segment.id(), segment);
        return segment;
    }

    @Override
    public Optional<Segment> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Segment> findByEnvironmentAndKey(String environmentKey, String key) {
        return byId.values().stream()
                .filter(s -> s.environmentKey().equals(environmentKey) && s.key().equals(key))
                .findFirst();
    }

    @Override
    public List<Segment> findAllByEnvironment(String environmentKey) {
        return byId.values().stream()
                .filter(s -> s.environmentKey().equals(environmentKey))
                .sorted((a, b) -> a.key().compareTo(b.key()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByEnvironmentAndKey(String environmentKey, String key) {
        return findByEnvironmentAndKey(environmentKey, key).isPresent();
    }

    @Override
    public void deleteById(String id) {
        byId.remove(id);
    }
}
