package com.appointment.dto;

import com.appointment.model.Payment;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Payment entity.
 *
 * Design Pattern: DTO Pattern
 * - Separates payment representation from domain model
 * - Hides sensitive payment gateway details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentDto {

    // Payment ID
    private UUID id;

    // Associated appointment ID
    private UUID appointmentId;

    // Payment amount
    private BigDecimal amount;

    // Payment status
    private Payment.PaymentStatus status;

    // Payment method used
    private Payment.PaymentMethod paymentMethod;

    // Transaction ID (masked for security)
    // Example: "txn_****1234" instead of full ID
    private String transactionId;

    // Failure reason (if failed)
    private String failureReason;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * DTO for initiating payment.
     *
     * Design Pattern: Command Pattern
     * - Encapsulates payment initiation request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitiateRequest {
        @NotNull(message = "Appointment ID is required")
        private UUID appointmentId;

        @NotNull(message = "Payment method is required")
        private Payment.PaymentMethod paymentMethod;

        // Optional payment gateway specific data
        private String paymentToken; // Token from client-side payment SDK
        private String returnUrl; // URL to return after payment
    }

    /**
     * DTO for payment gateway callback.
     *
     * Design Pattern: Adapter Pattern
     * - Adapts external payment gateway response to internal format
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayCallback {
        private String transactionId;
        private String status; // SUCCESS, FAILED, PENDING
        private String gatewayResponse; // Raw response from gateway
        private String signature; // HMAC signature for verification
    }

    /**
     * DTO for refund request.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequest {
        private String reason; // Reason for refund
    }
}
