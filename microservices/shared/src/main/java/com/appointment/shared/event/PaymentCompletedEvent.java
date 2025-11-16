package com.appointment.shared.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published when payment is successfully completed.
 *
 * Use Case in Production:
 * - Payment Service publishes this after successful payment
 * - Appointment Service consumes to update appointment status to CONFIRMED
 * - Notification Service consumes to send payment confirmation email
 * - Accounting Service consumes for financial records (future)
 *
 * Why Kafka for this?
 * - Payment confirmation doesn't need to be instant
 * - Appointment can be confirmed asynchronously (eventual consistency)
 * - Decouples payment gateway response from appointment confirmation
 * - If Appointment Service is down, event is retained and processed later
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class PaymentCompletedEvent extends BaseEvent {

    // Payment information
    private UUID paymentId; // ID of the payment record
    private UUID appointmentId; // Related appointment
    private BigDecimal amount; // Amount paid
    private String paymentMethod; // How payment was made (CARD, UPI, etc.)
    private String transactionId; // Payment gateway transaction ID

    // Patient information (for notifications)
    private UUID patientId;
    private String patientEmail;
    private String patientName;

    // Doctor information (for notifications)
    private UUID doctorId;
    private String doctorName;

    /**
     * Constructor with all required fields.
     *
     * @param paymentId Payment record ID
     * @param appointmentId Related appointment ID
     * @param amount Payment amount
     * @param paymentMethod Payment method used
     * @param transactionId Gateway transaction ID
     * @param patientId Patient ID
     * @param patientEmail Patient email for notification
     * @param patientName Patient name
     * @param doctorId Doctor ID
     * @param doctorName Doctor name
     */
    public PaymentCompletedEvent(
            UUID paymentId,
            UUID appointmentId,
            BigDecimal amount,
            String paymentMethod,
            String transactionId,
            UUID patientId,
            String patientEmail,
            String patientName,
            UUID doctorId,
            String doctorName
    ) {
        // Initialize base event fields
        super.initializeEvent("PaymentCompleted");

        // Set payment-specific fields
        this.paymentId = paymentId;
        this.appointmentId = appointmentId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.patientId = patientId;
        this.patientEmail = patientEmail;
        this.patientName = patientName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
    }
}
