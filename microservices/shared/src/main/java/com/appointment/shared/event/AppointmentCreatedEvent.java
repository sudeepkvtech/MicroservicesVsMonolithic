package com.appointment.shared.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Event published when a new appointment is created.
 *
 * Design Pattern: Event Sourcing (lightweight version)
 * - Event contains all data needed by consumers
 * - Immutable record of what happened
 * - Multiple services can react independently
 *
 * Use Case in Production:
 * - Appointment Service publishes this event after creating appointment
 * - Payment Service consumes to create payment record
 * - Notification Service consumes to send confirmation email
 * - Analytics Service consumes for reporting (future)
 *
 * Why Kafka for this?
 * - Multiple consumers need to react (Payment, Notification)
 * - Asynchronous processing is acceptable (eventual consistency)
 * - Non-blocking - appointment creation doesn't wait for payment/notification
 * - Fault tolerance - if a consumer is down, event is retained
 */
@Data
@EqualsAndHashCode(callSuper = true) // Include parent class fields in equals/hashCode
@NoArgsConstructor
@SuperBuilder
public class AppointmentCreatedEvent extends BaseEvent {

    // Appointment information
    private UUID appointmentId; // ID of the created appointment
    private UUID patientId; // Patient who booked
    private String patientEmail; // For notifications
    private String patientName; // For notifications
    private String patientPhone; // For SMS notifications

    // Doctor information
    private UUID doctorId; // Doctor for the appointment
    private String doctorName; // For notifications
    private String doctorSpecialization; // For notifications
    private String doctorEmail; // Doctor notification

    // Time slot information
    private UUID timeSlotId; // Time slot that was booked
    private LocalDate appointmentDate; // Date of appointment
    private LocalTime appointmentStartTime; // Start time
    private LocalTime appointmentEndTime; // End time

    // Payment information
    private BigDecimal consultationFee; // Amount to be paid
    private String reasonForVisit; // Optional patient reason

    /**
     * Constructor with all required fields.
     * Automatically initializes base event fields.
     *
     * @param appointmentId ID of created appointment
     * @param patientId Patient ID
     * @param patientEmail Patient email
     * @param patientName Patient name
     * @param doctorId Doctor ID
     * @param doctorName Doctor name
     * @param doctorSpecialization Doctor specialization
     * @param timeSlotId Time slot ID
     * @param appointmentDate Appointment date
     * @param appointmentStartTime Start time
     * @param appointmentEndTime End time
     * @param consultationFee Fee amount
     */
    public AppointmentCreatedEvent(
            UUID appointmentId,
            UUID patientId,
            String patientEmail,
            String patientName,
            String patientPhone,
            UUID doctorId,
            String doctorName,
            String doctorSpecialization,
            String doctorEmail,
            UUID timeSlotId,
            LocalDate appointmentDate,
            LocalTime appointmentStartTime,
            LocalTime appointmentEndTime,
            BigDecimal consultationFee,
            String reasonForVisit
    ) {
        // Initialize base event fields (eventId, timestamp, eventType, version)
        super.initializeEvent("AppointmentCreated");

        // Set appointment-specific fields
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.patientEmail = patientEmail;
        this.patientName = patientName;
        this.patientPhone = patientPhone;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.doctorSpecialization = doctorSpecialization;
        this.doctorEmail = doctorEmail;
        this.timeSlotId = timeSlotId;
        this.appointmentDate = appointmentDate;
        this.appointmentStartTime = appointmentStartTime;
        this.appointmentEndTime = appointmentEndTime;
        this.consultationFee = consultationFee;
        this.reasonForVisit = reasonForVisit;
    }
}
