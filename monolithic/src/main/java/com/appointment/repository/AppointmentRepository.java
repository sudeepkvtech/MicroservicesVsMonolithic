package com.appointment.repository;

import com.appointment.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Appointment entity.
 *
 * Design Pattern: Repository Pattern
 * - Central place for appointment data access
 * - Abstracts complex queries from business logic
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Find all appointments for a patient.
     * Used for patient's appointment history.
     *
     * @param patientId Patient's ID
     * @return List of appointments for patient
     */
    List<Appointment> findByPatientId(UUID patientId);

    /**
     * Find all appointments for a patient with pagination.
     * Used for paginated appointment history.
     *
     * @param patientId Patient's ID
     * @param pageable Pagination parameters
     * @return Page of appointments
     */
    Page<Appointment> findByPatientId(UUID patientId, Pageable pageable);

    /**
     * Find all appointments for a doctor.
     * Used for doctor's schedule view.
     *
     * @param doctorId Doctor's ID
     * @return List of appointments for doctor
     */
    List<Appointment> findByDoctorId(UUID doctorId);

    /**
     * Find appointments for a doctor with pagination.
     *
     * @param doctorId Doctor's ID
     * @param pageable Pagination parameters
     * @return Page of appointments
     */
    Page<Appointment> findByDoctorId(UUID doctorId, Pageable pageable);

    /**
     * Find appointments by status.
     * Used for filtering appointments (e.g., all pending, all completed).
     *
     * @param status Appointment status
     * @return List of appointments with given status
     */
    List<Appointment> findByStatus(Appointment.AppointmentStatus status);

    /**
     * Find patient's appointments by status.
     * Used for patient dashboard (e.g., "My Upcoming Appointments").
     *
     * @param patientId Patient's ID
     * @param status Appointment status
     * @return List of patient's appointments with status
     */
    List<Appointment> findByPatientIdAndStatus(
            UUID patientId,
            Appointment.AppointmentStatus status
    );

    /**
     * Find doctor's appointments by status.
     * Used for doctor dashboard.
     *
     * @param doctorId Doctor's ID
     * @param status Appointment status
     * @return List of doctor's appointments with status
     */
    List<Appointment> findByDoctorIdAndStatus(
            UUID doctorId,
            Appointment.AppointmentStatus status
    );

    /**
     * Find appointment with all related entities loaded.
     * Prevents N+1 query problem.
     *
     * Design Pattern: Eager Loading Pattern
     * - Loads patient, doctor, timeSlot, and payment in one query
     * - Avoids lazy loading exceptions
     * - More efficient than multiple queries
     *
     * @param id Appointment ID
     * @return Optional containing appointment with all relationships loaded
     */
    @Query("SELECT a FROM Appointment a " +
            "LEFT JOIN FETCH a.patient " + // Load patient
            "LEFT JOIN FETCH a.doctor " + // Load doctor
            "LEFT JOIN FETCH a.timeSlot " + // Load time slot
            "LEFT JOIN FETCH a.payment " + // Load payment
            "WHERE a.id = :id")
    Optional<Appointment> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Find upcoming appointments for a patient.
     * Used for "Upcoming Appointments" feature.
     *
     * @param patientId Patient's ID
     * @param currentDate Current date
     * @return List of future appointments
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN a.timeSlot ts " + // Join with time slot to access date
            "WHERE a.patient.id = :patientId " +
            "AND ts.slotDate >= :currentDate " + // Future dates
            "AND a.status IN ('PENDING', 'CONFIRMED') " + // Active statuses
            "ORDER BY ts.slotDate, ts.startTime")
    List<Appointment> findUpcomingAppointmentsByPatient(
            @Param("patientId") UUID patientId,
            @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find upcoming appointments for a doctor.
     * Used for doctor's schedule.
     *
     * @param doctorId Doctor's ID
     * @param currentDate Current date
     * @return List of future appointments
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN a.timeSlot ts " +
            "WHERE a.doctor.id = :doctorId " +
            "AND ts.slotDate >= :currentDate " +
            "AND a.status IN ('PENDING', 'CONFIRMED') " +
            "ORDER BY ts.slotDate, ts.startTime")
    List<Appointment> findUpcomingAppointmentsByDoctor(
            @Param("doctorId") UUID doctorId,
            @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find past appointments for a patient.
     * Used for appointment history.
     *
     * @param patientId Patient's ID
     * @param currentDate Current date
     * @return List of past appointments
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN a.timeSlot ts " +
            "WHERE a.patient.id = :patientId " +
            "AND ts.slotDate < :currentDate " + // Past dates
            "ORDER BY ts.slotDate DESC, ts.startTime DESC") // Most recent first
    List<Appointment> findPastAppointmentsByPatient(
            @Param("patientId") UUID patientId,
            @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find appointments on a specific date for a doctor.
     * Used for doctor's daily schedule.
     *
     * @param doctorId Doctor's ID
     * @param date Specific date
     * @return List of appointments on date
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN FETCH a.patient " + // Eager load patient
            "JOIN FETCH a.timeSlot ts " + // Eager load time slot
            "WHERE a.doctor.id = :doctorId " +
            "AND ts.slotDate = :date " +
            "ORDER BY ts.startTime")
    List<Appointment> findDoctorAppointmentsOnDate(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Count appointments by status for a patient.
     * Used for dashboard statistics.
     *
     * @param patientId Patient's ID
     * @param status Appointment status
     * @return Count of appointments
     */
    long countByPatientIdAndStatus(UUID patientId, Appointment.AppointmentStatus status);

    /**
     * Count appointments by status for a doctor.
     * Used for doctor analytics.
     *
     * @param doctorId Doctor's ID
     * @param status Appointment status
     * @return Count of appointments
     */
    long countByDoctorIdAndStatus(UUID doctorId, Appointment.AppointmentStatus status);

    /**
     * Find appointments that need confirmation.
     * Used for automated reminder jobs.
     *
     * @param date Date to check
     * @return List of pending appointments for date
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN FETCH a.patient " +
            "JOIN FETCH a.doctor " +
            "JOIN FETCH a.timeSlot ts " +
            "WHERE ts.slotDate = :date " +
            "AND a.status = 'PENDING'")
    List<Appointment> findPendingAppointmentsForDate(@Param("date") LocalDate date);

    /**
     * Find appointments with completed payments.
     * Used for financial reconciliation.
     *
     * @return List of appointments with completed payments
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN a.payment p " + // Must have payment
            "WHERE p.status = 'COMPLETED'")
    List<Appointment> findAppointmentsWithCompletedPayments();

    /**
     * Find appointments between two dates for a patient.
     * Used for date range queries.
     *
     * @param patientId Patient's ID
     * @param startDate Range start
     * @param endDate Range end
     * @return List of appointments in range
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN a.timeSlot ts " +
            "WHERE a.patient.id = :patientId " +
            "AND ts.slotDate BETWEEN :startDate AND :endDate " +
            "ORDER BY ts.slotDate, ts.startTime")
    List<Appointment> findPatientAppointmentsBetweenDates(
            @Param("patientId") UUID patientId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Check if patient has an appointment with doctor on specific date.
     * Used to prevent duplicate bookings.
     *
     * @param patientId Patient's ID
     * @param doctorId Doctor's ID
     * @param date Appointment date
     * @return true if appointment exists
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
            "JOIN a.timeSlot ts " +
            "WHERE a.patient.id = :patientId " +
            "AND a.doctor.id = :doctorId " +
            "AND ts.slotDate = :date " +
            "AND a.status IN ('PENDING', 'CONFIRMED')") // Exclude cancelled
    boolean existsActiveAppointmentForPatientWithDoctorOnDate(
            @Param("patientId") UUID patientId,
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Find all appointments for a specific time slot.
     * Used to check slot booking history.
     *
     * @param slotId Time slot ID
     * @return List of appointments for slot
     */
    List<Appointment> findByTimeSlotId(UUID slotId);

    /**
     * Find completed appointments for a doctor in date range.
     * Used for doctor performance analytics.
     *
     * @param doctorId Doctor's ID
     * @param startDate Range start
     * @param endDate Range end
     * @return List of completed appointments
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN a.timeSlot ts " +
            "WHERE a.doctor.id = :doctorId " +
            "AND a.status = 'COMPLETED' " +
            "AND ts.slotDate BETWEEN :startDate AND :endDate")
    List<Appointment> findCompletedAppointmentsByDoctorInRange(
            @Param("doctorId") UUID doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Get appointment statistics for a doctor.
     * Returns counts grouped by status.
     *
     * Design Pattern: DTO Projection
     * - Returns custom object instead of entity
     * - More efficient for statistics queries
     *
     * @param doctorId Doctor's ID
     * @return List of status counts
     */
    @Query("SELECT a.status as status, COUNT(a) as count " +
            "FROM Appointment a " +
            "WHERE a.doctor.id = :doctorId " +
            "GROUP BY a.status")
    List<Object[]> getAppointmentStatsByDoctor(@Param("doctorId") UUID doctorId);
    // Returns array of [status, count] pairs
}
