package com.thirikkale.userservice.service;

import com.thirikkale.userservice.model.IdSequence;
import com.thirikkale.userservice.repository.IdSequenceRepository;
import com.thirikkale.userservice.util.ReadableIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating sequential IDs in a thread-safe manner
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdSequenceService {

    private final IdSequenceRepository idSequenceRepository;

    /**
     * Get the next sequential number for the given entity
     * Thread-safe using pessimistic locking
     * 
     * @param entityName The entity name (e.g., "RIDER", "DRIVER", "ADMIN")
     * @return The next sequential number
     */
    @Transactional
    public Long getNextValue(String entityName) {
        IdSequence sequence = idSequenceRepository.findByEntityNameWithLock(entityName)
                .orElseGet(() -> {
                    // Create new sequence if doesn't exist
                    log.info("Creating new ID sequence for entity: {}", entityName);
                    IdSequence newSequence = IdSequence.builder()
                            .entityName(entityName)
                            .nextValue(1L)
                            .build();
                    return idSequenceRepository.save(newSequence);
                });

        Long currentValue = sequence.getNextValue();
        sequence.setNextValue(currentValue + 1);
        idSequenceRepository.save(sequence);

        log.debug("Generated sequence {} for entity: {}", currentValue, entityName);
        return currentValue;
    }

    /**
     * Generate a readable ID with the given prefix
     * 
     * @param prefix     The prefix (e.g., "R" for Rider, "D" for Driver)
     * @param entityName The entity name for sequence tracking
     * @return Formatted readable ID (e.g., "R00001")
     */
    @Transactional
    public String generateReadableId(String prefix, String entityName) {
        Long nextValue = getNextValue(entityName);
        return ReadableIdGenerator.generate(prefix, nextValue);
    }

    /**
     * Initialize all sequences if they don't exist
     * Should be called on application startup
     */
    @Transactional
    public void initializeSequences() {
        initializeSequenceIfNotExists("RIDER");
        initializeSequenceIfNotExists("DRIVER");
        initializeSequenceIfNotExists("ADMIN");
        initializeSequenceIfNotExists("VEHICLE");
        initializeSequenceIfNotExists("DOCUMENT");
        log.info("ID sequences initialized");
    }

    /**
     * Initialize a single sequence if it doesn't exist
     */
    private void initializeSequenceIfNotExists(String entityName) {
        if (!idSequenceRepository.existsById(entityName)) {
            IdSequence sequence = IdSequence.builder()
                    .entityName(entityName)
                    .nextValue(1L)
                    .build();
            idSequenceRepository.save(sequence);
            log.info("Initialized ID sequence for: {}", entityName);
        }
    }

    /**
     * Get current value without incrementing (for testing/debugging)
     */
    @Transactional(readOnly = true)
    public Long getCurrentValue(String entityName) {
        return idSequenceRepository.findById(entityName)
                .map(IdSequence::getNextValue)
                .orElse(1L);
    }

    /**
     * Reset sequence to a specific value (use with caution!)
     */
    @Transactional
    public void resetSequence(String entityName, Long value) {
        IdSequence sequence = idSequenceRepository.findById(entityName)
                .orElseThrow(() -> new IllegalArgumentException("Sequence not found: " + entityName));
        sequence.setNextValue(value);
        idSequenceRepository.save(sequence);
        log.warn("Sequence {} reset to value: {}", entityName, value);
    }
}
