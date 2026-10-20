package com.toggleboard.api.service;

import com.toggleboard.api.domain.EvaluationResult;
import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagEvaluator;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.Variant;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.InvalidFlagException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.FlagRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FlagService {

    private final FlagRepository flagRepository;
    private final EnvironmentService environmentService;
    private final FlagEvaluator flagEvaluator;

    public FlagService(FlagRepository flagRepository, EnvironmentService environmentService, FlagEvaluator flagEvaluator) {
        this.flagRepository = flagRepository;
        this.environmentService = environmentService;
        this.flagEvaluator = flagEvaluator;
    }

    public Flag create(String environmentKey, String key, String name, String description, FlagType type,
                        boolean enabled, int rolloutPercentage, List<Variant> variants, String defaultVariantKey) {
        environmentService.getByKey(environmentKey);

        if (flagRepository.existsByEnvironmentAndKey(environmentKey, key)) {
            throw new ConflictException("Flag '" + key + "' already exists in environment '" + environmentKey + "'");
        }
        validate(type, rolloutPercentage, variants, defaultVariantKey);

        Instant now = Instant.now();
        Flag flag = new Flag(UUID.randomUUID().toString(), key, environmentKey, name, description, type,
                enabled, rolloutPercentage, variants, defaultVariantKey, now, now);
        return flagRepository.save(flag);
    }

    public Flag update(String environmentKey, String key, String name, String description, boolean enabled,
                        int rolloutPercentage, List<Variant> variants, String defaultVariantKey) {
        Flag existing = getByKey(environmentKey, key);
        validate(existing.type(), rolloutPercentage, variants, defaultVariantKey);

        Flag updated = new Flag(existing.id(), existing.key(), existing.environmentKey(), name, description,
                existing.type(), enabled, rolloutPercentage, variants, defaultVariantKey, existing.createdAt(), Instant.now());
        return flagRepository.save(updated);
    }

    public Flag getByKey(String environmentKey, String key) {
        return flagRepository.findByEnvironmentAndKey(environmentKey, key)
                .orElseThrow(() -> new NotFoundException("Flag '" + key + "' not found in environment '" + environmentKey + "'"));
    }

    public List<Flag> listByEnvironment(String environmentKey) {
        environmentService.getByKey(environmentKey);
        return flagRepository.findAllByEnvironment(environmentKey);
    }

    public void delete(String environmentKey, String key) {
        Flag existing = getByKey(environmentKey, key);
        flagRepository.deleteById(existing.id());
    }

    public EvaluationResult evaluate(String environmentKey, String key, String subjectKey) {
        Flag flag = getByKey(environmentKey, key);
        return flagEvaluator.evaluate(flag, subjectKey);
    }

    private void validate(FlagType type, int rolloutPercentage, List<Variant> variants, String defaultVariantKey) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new InvalidFlagException("rolloutPercentage must be between 0 and 100");
        }
        if (type == FlagType.MULTIVARIATE) {
            if (variants == null || variants.isEmpty()) {
                throw new InvalidFlagException("Multivariate flags require at least one variant");
            }
            int totalWeight = variants.stream().mapToInt(Variant::weight).sum();
            if (totalWeight != 100) {
                throw new InvalidFlagException("Variant weights must sum to 100, got " + totalWeight);
            }
            boolean hasDefault = variants.stream().anyMatch(v -> v.key().equals(defaultVariantKey));
            if (defaultVariantKey == null || !hasDefault) {
                throw new InvalidFlagException("defaultVariantKey must match one of the variant keys");
            }
        }
    }
}
