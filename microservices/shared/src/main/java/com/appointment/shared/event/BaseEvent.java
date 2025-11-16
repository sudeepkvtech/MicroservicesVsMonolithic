package com.appointment.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all Kafka events.
 *
 * Design Pattern: Event-Driven Architecture
 * - Events represent "something that happened" in the past tense
 * - Immutable (no setters except for deserialization)
 * - Self-contained with all necessary data
 *
 * Design Pattern: Template Method Pattern
 * - Provides common structure for all events
 * - Ensures consistent event metadata
 *
 * Why Event-Driven?
 * - Decouples services (publisher doesn't know about consumers)
 * - Asynchronous processing (non-blocking)
 * - Multiple consumers can react to same event
 * - Supports eventual consistency
 * - Easier to add new features (just add new consumer)
 */
@Data // Lombok: Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Required for JSON deserialization
@SuperBuilder // Lombok: Builder pattern that works with inheritance
public abstract class BaseEvent {

    /**
     * Unique identifier for this event instance.
     * Used for:
     * - Event deduplication (prevent processing same event twice)
     * - Event tracing and debugging
     * - Idempotency checks
     */
    private UUID eventId;

    /**
     * Timestamp when event was created.
     * Used for:
     * - Event ordering (though Kafka maintains order per partition)
     * - Event expiration checks
     * - Auditing and debugging
     */
    @JsonSerialize(using = LocalDateTimeSerializer.class) // Jackson: Custom serializer for LocalDateTime
    @JsonDeserialize(using = LocalDateTimeDeserializer.class) // Jackson: Custom deserializer
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") // ISO 8601 format
    private LocalDateTime timestamp;

    /**
     * Type of the event (e.g., "AppointmentCreated").
     * Used for:
     * - Event routing in consumers
     * - Logging and monitoring
     * - Event filtering
     */
    private String eventType;

    /**
     * Version of the event schema.
     * Used for:
     * - Schema evolution (backward/forward compatibility)
     * - Consumer can handle different versions
     * - Gradual migration when changing event structure
     *
     * Example: If we add a new field, increment version from "1.0" to "1.1"
     * Old consumers can still process "1.0" events
     * New consumers can handle both "1.0" and "1.1"
     */
    private String version;

    /**
     * Initialize common event fields.
     * Called by subclasses in their constructors.
     *
     * @param eventType Type of the event
     */
    protected void initializeEvent(String eventType) {
        // Generate unique event ID
        this.eventId = UUID.randomUUID();
        // Set current timestamp
        this.timestamp = LocalDateTime.now();
        // Set event type
        this.eventType = eventType;
        // Set initial version
        this.version = "1.0";
    }
}
