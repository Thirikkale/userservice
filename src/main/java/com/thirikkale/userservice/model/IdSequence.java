package com.thirikkale.userservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity for managing sequential ID generation
 * Used to generate human-readable IDs like R00001, D00001, etc.
 */
@Entity
@Table(name = "id_sequences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdSequence {

    @Id
    @Column(name = "entity_name", length = 50)
    private String entityName;

    @Column(name = "next_value", nullable = false)
    @Builder.Default
    private Long nextValue = 1L;

    @Version
    @Column(name = "version")
    private Long version;
}
