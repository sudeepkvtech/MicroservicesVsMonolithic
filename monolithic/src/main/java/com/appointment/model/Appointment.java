package com.appointment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Appointment Entity - Represents a scheduled appointment between a patient and doctor.
 *
 * Design Pattern: Entity Pattern (DDD)
 * - Core aggregate in the appointment scheduling domain
 * - Orchestrates relationships between Patient, Doctor, and TimeSlot
 *
 * Design Pattern: State Pattern
 * - Appointment status represents different states in appointment lifecycle
 * - State transitions have business rules
 *
 * SOLID Principles:
 * - Single Responsibility: Manages appointment data and state transitions
 * - Open/Closed: New appointment statuses can be added without modifying existing code
 */
@Entity // JPA entity annotation
@Table(name = "appointments",
        indexes = {
                // Index for finding patient's appointments
                @Index(name = "idx_appointment_patient", columnList = "patient_id"),
                // Index for finding doctor's appointments
                @Index(name = "idx_appointment_doctor", columnList = "doctor_id"),
                // Index for finding appointments by status
                @Index(name = "idx_appointment_status", columnList = "status"),
                // Composite index for time-based queries
                @Index(name = "idx_appointment_slot", columnList = "slot_id")
        })
@Getter // Lombok: Generate getters
@Setter // Lombok: Generate setters
@NoArgsConstructor // JPA requirement
@AllArgsConstructor // All-args constructor
@Builder // Builder pattern
public class Appointment extends BaseEntity {

    /**
     * Relationship: Many appointments belong to one patient.
     *
     * Design Pattern: Many-to-One Relationship
     * - Each appointment has exactly one patient
     */
    @ManyToOne(fetch = FetchType.LAZY, // Lazy loading for performance
            optional = false) // Appointment must have a patient
    @JoinColumn(name = "patient_id", // Foreign key column
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_appointment_patient"))
    private User patient;

    /**
     * Relationship: Many appointments belong to one doctor.
     *
     * Design Pattern: Many-to-One Relationship
     * - Each appointment has exactly one doctor
     */
    @ManyToOne(fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(name = "doctor_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_appointment_doctor"))
    private Doctor doctor;

    /**
     * Relationship: Many appointments can reference one time slot.
     * Note: This is not one-to-one because we keep historical reference
     * even when slot is deleted or modified.
     */
    @ManyToOne(fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(name = "slot_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_appointment_slot"))
    private TimeSlot timeSlot;

    /**
     * Current status of the appointment.
     *
     * Design Pattern: State Pattern
     * - Each status represents a state in the appointment lifecycle
     * - State transitions follow business rules
     *
     * Status Flow:
     * PENDING -> CONFIRMED -> COMPLETED
     *         -> CANCELLED
     */
    @Enumerated(EnumType.STRING) // Store enum as string for readability
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default // Default value for builder
    private AppointmentStatus status = AppointmentStatus.PENDING;

    /**
     * Patient's reason for visit.
     * Helps doctor prepare for appointment.
     */
    @Column(name = "reason_for_visit", columnDefinition = "TEXT")
    private String reasonForVisit;

    /**
     * Doctor's notes after appointment.
     * Medical records and observations.
     * Only populated after appointment is completed.
     */
    @Column(name = "doctor_notes", columnDefinition = "TEXT")
    private String doctorNotes;

    /**
     * Prescription given by doctor.
     * Only populated after appointment is completed.
     */
    @Column(name = "prescription", columnDefinition = "TEXT")
    private String prescription;

    /**
     * Relationship: One appointment has one payment.
     *
     * Design Pattern: One-to-One Relationship
     * - Bidirectional relationship with Payment entity
     * - Cascade operations to payment
     */
    @OneToOne(mappedBy = "appointment", // References 'appointment' field in Payment
            cascade = CascadeType.ALL, // Cascade all operations
            orphanRemoval = true, // Remove payment if appointment is deleted
            fetch = FetchType.LAZY)
    private Payment payment;

    /**
     * Business method to confirm the appointment.
     * Validates state transition rules.
     *
     * @throws IllegalStateException if appointment cannot be confirmed
     */
    public void confirm() {
        // Only PENDING appointments can be confirmed
        if (status != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending appointments can be confirmed. Current status: " + status);
        }
        // Transition to CONFIRMED state
        status = AppointmentStatus.CONFIRMED;
    }

    /**
     * Business method to cancel the appointment.
     * Validates state transition rules.
     *
     * @throws IllegalStateException if appointment cannot be cancelled
     */
    public void cancel() {
        // Cannot cancel already completed appointments
        if (status == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed appointments cannot be cancelled");
        }
        // Cannot cancel already cancelled appointments
        if (status == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Appointment is already cancelled");
        }
        // Transition to CANCELLED state
        status = AppointmentStatus.CANCELLED;

        // Free up the time slot (business logic)
        if (timeSlot != null) {
            timeSlot.cancelBooking();
        }
    }

    /**
     * Business method to mark appointment as completed.
     * Validates state transition rules.
     *
     * @throws IllegalStateException if appointment cannot be completed
     */
    public void complete() {
        // Only CONFIRMED appointments can be completed
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only confirmed appointments can be completed. Current status: " + status);
        }
        // Transition to COMPLETED state
        status = AppointmentStatus.COMPLETED;
    }

    /**
     * Business method to add doctor's notes.
     * Only allowed for confirmed or completed appointments.
     *
     * @param notes Doctor's notes
     * @throws IllegalStateException if notes cannot be added
     */
    public void addDoctorNotes(String notes) {
        // Validate appointment state
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Doctor notes can only be added to confirmed or completed appointments");
        }
        // Set the notes
        this.doctorNotes = notes;
    }

    /**
     * Business method to add prescription.
     * Only allowed for confirmed or completed appointments.
     *
     * @param prescription Prescription details
     * @throws IllegalStateException if prescription cannot be added
     */
    public void addPrescription(String prescription) {
        // Validate appointment state
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Prescription can only be added to confirmed or completed appointments");
        }
        // Set the prescription
        this.prescription = prescription;
    }

    /**
     * Business method to check if appointment can be rescheduled.
     *
     * @return true if appointment can be rescheduled
     */
    public boolean canBeRescheduled() {
        // Can reschedule if PENDING or CONFIRMED, but not if CANCELLED or COMPLETED
        return status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED;
    }

    /**
     * Business method to check if appointment requires payment.
     *
     * @return true if payment is required
     */
    public boolean requiresPayment() {
        // Payment required if no payment exists or payment is not completed
        return payment == null || !payment.isCompleted();
    }

    /**
     * Enumeration of possible appointment statuses.
     *
     * Design Pattern: Type-Safe Enumeration
     * - Compile-time type safety
     * - Self-documenting code
     * - IDE autocomplete support
     */
    public enum AppointmentStatus {
        /**
         * Appointment is created but not yet confirmed.
         * Waiting for payment or confirmation.
         */
        PENDING,

        /**
         * Appointment is confirmed and scheduled.
         * Patient and doctor are notified.
         */
        CONFIRMED,

        /**
         * Appointment was cancelled by patient or doctor.
         * Time slot is freed up.
         */
        CANCELLED,

        /**
         * Appointment took place and is completed.
         * Doctor may have added notes and prescription.
         */
        COMPLETED,

        /**
         * Patient did not show up for the appointment.
         * Different from cancellation for tracking purposes.
         */
        NO_SHOW
    }
}
