package com.appointment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * TimeSlot Entity - Represents a doctor's available time slot for appointments.
 *
 * Design Pattern: Entity Pattern (DDD)
 * - Represents availability in the domain model
 * - Encapsulates slot booking logic
 *
 * Design Pattern: Optimistic Locking (via @Version)
 * - Prevents double-booking in concurrent scenarios
 * - Handles race conditions when multiple users book same slot
 *
 * SOLID Principles:
 * - Single Responsibility: Manages time slot availability
 * - Encapsulation: Booking logic encapsulated in entity methods
 */
@Entity // JPA entity mapping
@Table(name = "time_slots",
        indexes = {
                // Composite index for efficient slot searches by doctor and date
                @Index(name = "idx_slot_doctor_date", columnList = "doctor_id, slot_date"),
                // Index for finding available slots quickly
                @Index(name = "idx_slot_available", columnList = "is_available")
        },
        uniqueConstraints = {
                // Prevent duplicate slots for same doctor at same time
                @UniqueConstraint(name = "uk_slot_doctor_date_time",
                        columnNames = {"doctor_id", "slot_date", "start_time"})
        })
@Getter // Lombok: Generate getters
@Setter // Lombok: Generate setters
@NoArgsConstructor // JPA requires no-arg constructor
@AllArgsConstructor // Constructor with all fields
@Builder // Builder pattern for fluent object creation
public class TimeSlot extends BaseEntity {

    /**
     * Relationship: Many time slots belong to one doctor.
     *
     * Design Pattern: Many-to-One Relationship
     * - Each slot is associated with exactly one doctor
     * - Foreign key relationship in database
     */
    @ManyToOne(fetch = FetchType.LAZY, // Lazy loading: Load doctor only when accessed
            optional = false) // Slot must have a doctor (cannot be null)
    @JoinColumn(name = "doctor_id", // Foreign key column name
            nullable = false, // Database constraint: cannot be null
            foreignKey = @ForeignKey(name = "fk_slot_doctor")) // Foreign key constraint name
    private Doctor doctor;

    /**
     * Date of the time slot.
     * Stores only the date part (not time).
     */
    @NotNull(message = "Slot date is required") // Validation: Cannot be null
    @Column(name = "slot_date", nullable = false) // DB column: required field
    private LocalDate slotDate;

    /**
     * Start time of the slot.
     * Example: 09:00, 14:30
     */
    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * End time of the slot.
     * Example: 09:30, 15:00
     * Duration is calculated as endTime - startTime.
     */
    @NotNull(message = "End time is required")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Indicates if the slot is currently available for booking.
     * false = slot is booked or blocked
     * true = slot is available
     *
     * Design Pattern: State Pattern
     * - Represents the state of the slot (available/unavailable)
     */
    @Column(name = "is_available", nullable = false)
    @Builder.Default // Default value when using builder
    private Boolean isAvailable = true;

    /**
     * Optional: Maximum number of appointments for this slot.
     * Allows for concurrent bookings if doctor can handle multiple patients.
     * Default: 1 (one patient per slot)
     */
    @Column(name = "max_bookings", nullable = false)
    @Builder.Default
    private Integer maxBookings = 1;

    /**
     * Current number of bookings for this slot.
     * Used when maxBookings > 1 to track capacity.
     */
    @Column(name = "current_bookings", nullable = false)
    @Builder.Default
    private Integer currentBookings = 0;

    /**
     * Version field for optimistic locking.
     *
     * Design Pattern: Optimistic Locking
     * - Prevents lost updates in concurrent scenarios
     * - JPA increments version on each update
     * - If two transactions try to update same slot, second one fails
     *
     * Example scenario:
     * 1. User A and User B both try to book same slot
     * 2. Both read slot with version=1
     * 3. User A saves first, version becomes 2
     * 4. User B tries to save with version=1, gets OptimisticLockException
     * 5. User B must retry (slot already booked)
     */
    @Version // JPA optimistic locking annotation
    @Column(name = "version")
    private Long version;

    /**
     * Business method to book the time slot.
     * Implements booking logic with validation.
     *
     * @throws IllegalStateException if slot is not available or fully booked
     */
    public void book() {
        // Validate slot is available
        if (!isAvailable) {
            // Throw exception if slot already marked unavailable
            throw new IllegalStateException("Time slot is not available for booking");
        }

        // Validate capacity not exceeded
        if (currentBookings >= maxBookings) {
            // Throw exception if slot is fully booked
            throw new IllegalStateException("Time slot is fully booked");
        }

        // Increment booking counter
        currentBookings++;

        // Mark as unavailable if fully booked
        if (currentBookings >= maxBookings) {
            isAvailable = false;
        }
    }

    /**
     * Business method to cancel a booking and free up the slot.
     * Implements cancellation logic.
     */
    public void cancelBooking() {
        // Validate there are bookings to cancel
        if (currentBookings <= 0) {
            // Throw exception if no bookings to cancel
            throw new IllegalStateException("No bookings to cancel");
        }

        // Decrement booking counter
        currentBookings--;

        // Mark as available if space is now free
        if (currentBookings < maxBookings) {
            isAvailable = true;
        }
    }

    /**
     * Business method to block the slot (make unavailable).
     * Used when doctor wants to block time for breaks, meetings, etc.
     */
    public void block() {
        // Set availability flag to false
        isAvailable = false;
    }

    /**
     * Business method to unblock the slot (make available).
     * Used when doctor wants to open previously blocked time.
     */
    public void unblock() {
        // Set availability only if not fully booked
        if (currentBookings < maxBookings) {
            isAvailable = true;
        }
    }

    /**
     * Business method to check if slot is in the past.
     * Prevents booking of past slots.
     *
     * @return true if slot date/time has passed
     */
    public boolean isPast() {
        // Get current date and time
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // Check if slot date is before today
        if (slotDate.isBefore(today)) {
            return true;
        }

        // If slot is today, check if time has passed
        if (slotDate.isEqual(today) && startTime.isBefore(now)) {
            return true;
        }

        // Slot is in the future
        return false;
    }

    /**
     * Business method to get slot duration in minutes.
     *
     * @return Duration in minutes
     */
    public long getDurationMinutes() {
        // Calculate difference between start and end time
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    /**
     * Business method to check if this slot overlaps with another slot.
     * Used to prevent scheduling conflicts.
     *
     * @param other Another time slot
     * @return true if slots overlap
     */
    public boolean overlapsWith(TimeSlot other) {
        // Slots don't overlap if on different dates
        if (!this.slotDate.equals(other.slotDate)) {
            return false;
        }

        // Check time overlap using interval logic
        // Overlap if: (start1 < end2) AND (start2 < end1)
        return this.startTime.isBefore(other.endTime) &&
                other.startTime.isBefore(this.endTime);
    }
}
