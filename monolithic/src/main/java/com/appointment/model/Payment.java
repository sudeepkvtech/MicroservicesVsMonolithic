package com.appointment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Payment Entity - Represents a payment transaction for an appointment.
 *
 * Design Pattern: Entity Pattern (DDD)
 * - Represents financial transaction in the domain
 * - Encapsulates payment lifecycle and state transitions
 *
 * Design Pattern: State Pattern
 * - Payment status represents different states in payment lifecycle
 * - State transitions follow business rules
 *
 * SOLID Principles:
 * - Single Responsibility: Manages payment data and state
 * - Encapsulation: Payment logic contained within entity
 */
@Entity // JPA entity
@Table(name = "payments",
        indexes = {
                // Index for finding payments by appointment
                @Index(name = "idx_payment_appointment", columnList = "appointment_id"),
                // Index for finding payments by status
                @Index(name = "idx_payment_status", columnList = "status"),
                // Index for finding payments by transaction ID (for reconciliation)
                @Index(name = "idx_payment_transaction", columnList = "transaction_id")
        })
@Getter // Lombok: Generate getters
@Setter // Lombok: Generate setters
@NoArgsConstructor // JPA requirement
@AllArgsConstructor // All-args constructor
@Builder // Builder pattern
public class Payment extends BaseEntity {

    /**
     * Relationship: One payment belongs to one appointment.
     *
     * Design Pattern: One-to-One Relationship
     * - Bidirectional relationship with Appointment
     * - Each payment is for exactly one appointment
     */
    @OneToOne(fetch = FetchType.LAZY, // Lazy loading
            optional = false) // Payment must have an appointment
    @JoinColumn(name = "appointment_id", // Foreign key column
            nullable = false,
            unique = true, // One-to-one constraint
            foreignKey = @ForeignKey(name = "fk_payment_appointment"))
    private Appointment appointment;

    /**
     * Payment amount.
     * Uses BigDecimal for precise monetary calculations.
     *
     * Design Pattern: Value Object
     * - Money is a value object (immutable, compared by value)
     * - BigDecimal prevents floating-point errors
     */
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.0", inclusive = false,
            message = "Payment amount must be greater than zero")
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Current status of the payment.
     *
     * Design Pattern: State Pattern
     * Status Flow:
     * PENDING -> PROCESSING -> COMPLETED
     *         -> FAILED
     * COMPLETED -> REFUNDED (in case of cancellation)
     */
    @Enumerated(EnumType.STRING) // Store as string for readability
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Payment method used by patient.
     * Examples: CREDIT_CARD, DEBIT_CARD, UPI, WALLET, CASH
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    /**
     * Transaction ID from payment gateway.
     * Used for tracking and reconciliation with payment provider.
     * Unique identifier from Stripe, Razorpay, etc.
     */
    @Column(name = "transaction_id", unique = true, length = 255)
    private String transactionId;

    /**
     * Reference number for refunds.
     * Populated when payment is refunded.
     */
    @Column(name = "refund_reference", length = 255)
    private String refundReference;

    /**
     * Reason for failed payment.
     * Populated when status is FAILED.
     * Examples: "Insufficient funds", "Card declined", "Network error"
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * Business method to mark payment as processing.
     * Called when payment gateway request is initiated.
     *
     * @throws IllegalStateException if payment cannot be processed
     */
    public void markAsProcessing() {
        // Only PENDING payments can be processed
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending payments can be processed. Current status: " + status);
        }
        // Transition to PROCESSING state
        status = PaymentStatus.PROCESSING;
    }

    /**
     * Business method to mark payment as completed.
     * Called when payment gateway confirms successful payment.
     *
     * @param transactionId Transaction ID from payment gateway
     * @throws IllegalStateException if payment cannot be completed
     */
    public void markAsCompleted(String transactionId) {
        // Only PROCESSING payments can be completed
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only processing payments can be completed. Current status: " + status);
        }
        // Validate transaction ID is provided
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required for completed payment");
        }
        // Set transaction ID
        this.transactionId = transactionId;
        // Transition to COMPLETED state
        status = PaymentStatus.COMPLETED;
    }

    /**
     * Business method to mark payment as failed.
     * Called when payment gateway returns error.
     *
     * @param reason Reason for failure
     * @throws IllegalStateException if payment cannot be failed
     */
    public void markAsFailed(String reason) {
        // Only PENDING or PROCESSING payments can fail
        if (status != PaymentStatus.PENDING && status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only pending or processing payments can be failed. Current status: " + status);
        }
        // Set failure reason
        this.failureReason = reason;
        // Transition to FAILED state
        status = PaymentStatus.FAILED;
    }

    /**
     * Business method to refund the payment.
     * Called when appointment is cancelled and refund is issued.
     *
     * @param refundReference Reference number from refund transaction
     * @throws IllegalStateException if payment cannot be refunded
     */
    public void refund(String refundReference) {
        // Only COMPLETED payments can be refunded
        if (status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Only completed payments can be refunded. Current status: " + status);
        }
        // Validate refund reference is provided
        if (refundReference == null || refundReference.trim().isEmpty()) {
            throw new IllegalArgumentException("Refund reference is required");
        }
        // Set refund reference
        this.refundReference = refundReference;
        // Transition to REFUNDED state
        status = PaymentStatus.REFUNDED;
    }

    /**
     * Business method to check if payment is completed.
     *
     * @return true if payment status is COMPLETED
     */
    public boolean isCompleted() {
        // Check if status is COMPLETED
        return status == PaymentStatus.COMPLETED;
    }

    /**
     * Business method to check if payment can be retried.
     *
     * @return true if payment can be retried
     */
    public boolean canRetry() {
        // Can retry if payment failed
        return status == PaymentStatus.FAILED;
    }

    /**
     * Business method to check if payment can be refunded.
     *
     * @return true if payment can be refunded
     */
    public boolean canBeRefunded() {
        // Can refund only completed payments
        return status == PaymentStatus.COMPLETED;
    }

    /**
     * Enumeration of possible payment statuses.
     *
     * Design Pattern: Type-Safe Enumeration
     */
    public enum PaymentStatus {
        /**
         * Payment is created but not yet initiated.
         * Initial state when payment record is created.
         */
        PENDING,

        /**
         * Payment request sent to payment gateway.
         * Waiting for gateway response.
         */
        PROCESSING,

        /**
         * Payment successfully completed.
         * Money received and confirmed by payment gateway.
         */
        COMPLETED,

        /**
         * Payment failed.
         * Reason stored in failureReason field.
         */
        FAILED,

        /**
         * Payment was completed but later refunded.
         * Money returned to patient.
         */
        REFUNDED
    }

    /**
     * Enumeration of supported payment methods.
     *
     * Design Pattern: Strategy Pattern (used in payment processing)
     * - Each payment method may have different processing logic
     * - Extensible: New methods can be added easily
     */
    public enum PaymentMethod {
        /**
         * Credit card payment (Visa, Mastercard, Amex, etc.)
         */
        CREDIT_CARD,

        /**
         * Debit card payment
         */
        DEBIT_CARD,

        /**
         * Unified Payments Interface (India)
         */
        UPI,

        /**
         * Digital wallet (PayPal, Google Pay, etc.)
         */
        WALLET,

        /**
         * Cash payment at clinic
         */
        CASH,

        /**
         * Net banking / Bank transfer
         */
        NET_BANKING
    }
}
