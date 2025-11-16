package com.appointment.repository;

import com.appointment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Payment entity.
 *
 * Design Pattern: Repository Pattern
 * - Encapsulates payment data access logic
 * - Provides clean interface for financial operations
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find payment by appointment ID.
     * Used to retrieve payment details for an appointment.
     *
     * @param appointmentId Appointment's ID
     * @return Optional containing payment if found
     */
    Optional<Payment> findByAppointmentId(UUID appointmentId);

    /**
     * Find payment by transaction ID.
     * Used for payment gateway callbacks and reconciliation.
     *
     * @param transactionId Transaction ID from payment gateway
     * @return Optional containing payment
     */
    Optional<Payment> findByTransactionId(String transactionId);

    /**
     * Find all payments by status.
     * Used for payment processing and reporting.
     *
     * @param status Payment status
     * @return List of payments with given status
     */
    List<Payment> findByStatus(Payment.PaymentStatus status);

    /**
     * Find payments by payment method.
     * Used for analytics and reporting.
     *
     * @param paymentMethod Payment method
     * @return List of payments with method
     */
    List<Payment> findByPaymentMethod(Payment.PaymentMethod paymentMethod);

    /**
     * Find payments for a specific patient.
     * Used for patient's payment history.
     *
     * Design Pattern: Join Query
     * - Joins through appointment to get patient's payments
     *
     * @param patientId Patient's ID
     * @return List of patient's payments
     */
    @Query("SELECT p FROM Payment p " +
            "JOIN p.appointment a " + // Join with appointment
            "WHERE a.patient.id = :patientId " + // Filter by patient
            "ORDER BY p.createdAt DESC") // Most recent first
    List<Payment> findByPatientId(@Param("patientId") UUID patientId);

    /**
     * Find payments for a specific doctor.
     * Used for doctor's earnings report.
     *
     * @param doctorId Doctor's ID
     * @return List of doctor's payments
     */
    @Query("SELECT p FROM Payment p " +
            "JOIN p.appointment a " +
            "WHERE a.doctor.id = :doctorId " +
            "ORDER BY p.createdAt DESC")
    List<Payment> findByDoctorId(@Param("doctorId") UUID doctorId);

    /**
     * Find completed payments for a doctor.
     * Used for calculating doctor's earnings.
     *
     * @param doctorId Doctor's ID
     * @return List of completed payments
     */
    @Query("SELECT p FROM Payment p " +
            "JOIN p.appointment a " +
            "WHERE a.doctor.id = :doctorId " +
            "AND p.status = 'COMPLETED'")
    List<Payment> findCompletedPaymentsByDoctor(@Param("doctorId") UUID doctorId);

    /**
     * Calculate total earnings for a doctor.
     * Used for financial dashboard.
     *
     * Design Pattern: Aggregation Query
     * - Uses SUM function for calculation
     * - Returns BigDecimal for precise money arithmetic
     *
     * @param doctorId Doctor's ID
     * @return Total earnings (null if no payments)
     */
    @Query("SELECT SUM(p.amount) FROM Payment p " +
            "JOIN p.appointment a " +
            "WHERE a.doctor.id = :doctorId " +
            "AND p.status = 'COMPLETED'")
    BigDecimal calculateTotalEarningsByDoctor(@Param("doctorId") UUID doctorId);
    // SUM returns null if no records, not 0

    /**
     * Calculate total earnings for a doctor in date range.
     * Used for monthly/yearly reports.
     *
     * @param doctorId Doctor's ID
     * @param startDate Range start
     * @param endDate Range end
     * @return Total earnings in range
     */
    @Query("SELECT SUM(p.amount) FROM Payment p " +
            "WHERE p.appointment.doctor.id = :doctorId " +
            "AND p.status = 'COMPLETED' " +
            "AND p.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateEarningsInDateRange(
            @Param("doctorId") UUID doctorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find failed payments.
     * Used for payment retry and failure analysis.
     *
     * @return List of failed payments
     */
    @Query("SELECT p FROM Payment p " +
            "WHERE p.status = 'FAILED' " +
            "ORDER BY p.createdAt DESC")
    List<Payment> findFailedPayments();

    /**
     * Find pending payments older than specified minutes.
     * Used for timeout detection and cleanup.
     *
     * @param minutes Number of minutes
     * @param currentTime Current timestamp
     * @return List of stale pending payments
     */
    @Query("SELECT p FROM Payment p " +
            "WHERE p.status = 'PENDING' " +
            "AND p.createdAt < :cutoffTime") // Before cutoff
    List<Payment> findStalePendingPayments(@Param("cutoffTime") LocalDateTime cutoffTime);
    // Example usage: findStalePendingPayments(LocalDateTime.now().minusMinutes(30))

    /**
     * Find payments pending confirmation from gateway.
     * Used for reconciliation jobs.
     *
     * @return List of processing payments
     */
    @Query("SELECT p FROM Payment p " +
            "WHERE p.status = 'PROCESSING' " +
            "ORDER BY p.createdAt")
    List<Payment> findProcessingPayments();

    /**
     * Count payments by status.
     * Used for admin dashboard statistics.
     *
     * @param status Payment status
     * @return Number of payments with status
     */
    long countByStatus(Payment.PaymentStatus status);

    /**
     * Count payments by method.
     * Used for payment method popularity analysis.
     *
     * @param paymentMethod Payment method
     * @return Number of payments with method
     */
    long countByPaymentMethod(Payment.PaymentMethod paymentMethod);

    /**
     * Find refunded payments.
     * Used for refund tracking and reconciliation.
     *
     * @return List of refunded payments
     */
    @Query("SELECT p FROM Payment p " +
            "WHERE p.status = 'REFUNDED' " +
            "ORDER BY p.updatedAt DESC")
    List<Payment> findRefundedPayments();

    /**
     * Find payments by refund reference.
     * Used for refund confirmation tracking.
     *
     * @param refundReference Refund reference number
     * @return Optional containing payment
     */
    Optional<Payment> findByRefundReference(String refundReference);

    /**
     * Get payment statistics grouped by status.
     * Used for admin analytics dashboard.
     *
     * Design Pattern: DTO Projection
     * - Returns aggregated data instead of entities
     *
     * @return List of [status, count, total_amount] arrays
     */
    @Query("SELECT p.status, COUNT(p), SUM(p.amount) " +
            "FROM Payment p " +
            "GROUP BY p.status")
    List<Object[]> getPaymentStatistics();
    // Returns: [[COMPLETED, 150, 45000.00], [PENDING, 10, 3000.00], ...]

    /**
     * Get daily payment statistics for a date range.
     * Used for revenue charts and graphs.
     *
     * @param startDate Range start
     * @param endDate Range end
     * @return List of [date, count, total] arrays
     */
    @Query("SELECT CAST(p.createdAt AS date), COUNT(p), SUM(p.amount) " +
            "FROM Payment p " +
            "WHERE p.status = 'COMPLETED' " +
            "AND p.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY CAST(p.createdAt AS date) " +
            "ORDER BY CAST(p.createdAt AS date)")
    List<Object[]> getDailyPaymentStatistics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find payments by amount range.
     * Used for filtering and analysis.
     *
     * @param minAmount Minimum amount
     * @param maxAmount Maximum amount
     * @return List of payments in range
     */
    List<Payment> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Check if payment exists for appointment.
     * Used for validation.
     *
     * @param appointmentId Appointment's ID
     * @return true if payment exists
     */
    boolean existsByAppointmentId(UUID appointmentId);

    /**
     * Get total revenue for the platform.
     * Used for business metrics.
     *
     * @return Total completed payment amount
     */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'COMPLETED'")
    BigDecimal calculateTotalRevenue();

    /**
     * Get total refunded amount.
     * Used for financial reporting.
     *
     * @return Total refunded amount
     */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'REFUNDED'")
    BigDecimal calculateTotalRefunds();
}
