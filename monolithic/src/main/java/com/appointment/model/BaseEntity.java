package com.appointment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Entity class that provides common fields for all domain entities.
 *
 * Design Pattern: Template Method Pattern
 * - Defines common structure that all entities inherit
 * - Reduces code duplication (DRY principle)
 * - Provides consistent ID generation and auditing
 *
 * Design Pattern: Auditing Pattern
 * - Automatically tracks when entities are created and modified
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles common entity concerns
 * - Open/Closed: Open for extension (inheritance), closed for modification
 */
@MappedSuperclass // Indicates this class is inherited by other entities but not a table itself
@EntityListeners(AuditingEntityListener.class) // Enables automatic auditing of createdAt/updatedAt
@Getter // Lombok: Generates getter methods for all fields
@Setter // Lombok: Generates setter methods for all fields
public abstract class BaseEntity {

    /**
     * Primary key for all entities using UUID for global uniqueness.
     * UUID is preferred over auto-increment for:
     * - Distributed systems compatibility
     * - Security (non-sequential IDs)
     * - Offline ID generation capability
     */
    @Id // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.UUID) // Auto-generates UUID values
    @Column(name = "id", updatable = false, nullable = false) // Database column configuration
    private UUID id;

    /**
     * Timestamp when the entity was created.
     * Automatically set by JPA auditing on first persist.
     */
    @CreatedDate // JPA Auditing: Automatically sets value on entity creation
    @Column(name = "created_at", nullable = false, updatable = false) // Cannot be updated after creation
    private LocalDateTime createdAt;

    /**
     * Timestamp when the entity was last modified.
     * Automatically updated by JPA auditing on every update.
     */
    @LastModifiedDate // JPA Auditing: Automatically updates value on entity modification
    @Column(name = "updated_at", nullable = false) // Required field that can be updated
    private LocalDateTime updatedAt;

    /**
     * Hook method called before entity is persisted.
     * Sets timestamps if not already set (for manual entity creation in tests).
     */
    @PrePersist // JPA lifecycle callback: Called before entity is saved to database
    protected void onCreate() {
        // Initialize timestamps if null (defensive programming)
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Hook method called before entity is updated.
     * Updates the last modified timestamp.
     */
    @PreUpdate // JPA lifecycle callback: Called before entity is updated in database
    protected void onUpdate() {
        // Always update the modification timestamp
        updatedAt = LocalDateTime.now();
    }

    /**
     * Overridden equals method for entity comparison.
     * Entities are equal if they have the same ID.
     *
     * @param o Object to compare with
     * @return true if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        // Check if comparing to itself
        if (this == o) return true;
        // Check if comparing to null or different class
        if (o == null || getClass() != o.getClass()) return false;
        // Cast to BaseEntity and compare IDs
        BaseEntity that = (BaseEntity) o;
        // Entities are equal if IDs are not null and match
        return id != null && id.equals(that.id);
    }

    /**
     * Overridden hashCode method consistent with equals.
     * Uses ID for hash code generation.
     *
     * @return hash code of the entity
     */
    @Override
    public int hashCode() {
        // Use a constant for new entities (id is null) to maintain hash consistency
        // Use id.hashCode() for persisted entities
        return id != null ? id.hashCode() : 0;
    }
}
