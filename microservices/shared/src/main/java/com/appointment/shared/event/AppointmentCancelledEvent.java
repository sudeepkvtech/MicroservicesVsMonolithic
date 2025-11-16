package com.appointment.shared.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Event published when an appointment is cancelled.
 *
 * Use Case in Production:
 * - Appointment Service publishes this after cancelling appointment
 * - Payment Service consumes to process refund
 * - Notification Service consumes to send cancellation email
 * - Doctor Service consumes to free up the time slot (optional, can be REST)
 *
 * Why Kafka for this?
 * - Refund processing can be asynchronous
 * - Notification sending doesn't block cancellation
 * - Multiple actions need to happen (refund + notify)
 * - Audit trail of cancellations
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class AppointmentCancelledEvent extends BaseEvent {

    // Appointment information
    private UUID appointmentId; // Cancelled appointment ID
    private UUID patientId; // Who cancelled (could be patient or doctor)
    private UUID doctorId; // Doctor for the appointment
    private UUID timeSlotId; // Time slot to be freed

    // Cancellation details
    private String cancelledBy; // "PATIENT" or "DOCTOR" or "SYSTEM"
    private String cancellationReason; // Optional reason

    // Appointment details (for notifications)
    private LocalDate appointmentDate;
    private LocalTime appointmentStartTime;
    private String patientName;
    private String patientEmail;
    private String doctorName;
    private String doctorSpecialization;

    // Payment information (for refund processing)
    private UUID paymentId; // Payment to be refunded (if exists)
    private boolean refundRequired; // Whether refund is needed

    /**
     * Constructor with all required fields.
     *
     * @param appointmentId Cancelled appointment ID
     * @param patientId Patient ID
     * @param doctorId Doctor ID
     * @param timeSlotId Time slot ID
     * @param cancelledBy Who cancelled
     * @param cancellationReason Reason for cancellation
     * @param appointmentDate Appointment date
     * @param appointmentStartTime Appointment time
     * @param patientName Patient name
     * @param patientEmail Patient email
     * @param doctorName Doctor name
     * @param doctorSpecialization Doctor specialization
     * @param paymentId Payment ID (can be null)
     * @param refundRequired Whether refund is needed
     */
    public AppointmentCancelledEvent(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            UUID timeSlotId,
            String cancelledBy,
            String cancellationReason,
            LocalDate appointmentDate,
            LocalTime appointmentStartTime,
            String patientName,
            String patientEmail,
            String doctorName,
            String doctorSpecialization,
            UUID paymentId,
            boolean refundRequired
    ) {
        // Initialize base event fields
        super.initializeEvent("AppointmentCancelled");

        // Set cancellation-specific fields
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.timeSlotId = timeSlotId;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = cancellationReason;
        this.appointmentDate = appointmentDate;
        this.appointmentStartTime = appointmentStartTime;
        this.patientName = patientName;
        this.patientEmail = patientEmail;
        this.doctorName = doctorName;
        this.doctorSpecialization = doctorSpecialization;
        this.paymentId = paymentId;
        this.refundRequired = refundRequired;
    }
}
