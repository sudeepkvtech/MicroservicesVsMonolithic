package com.appointment.repository;

import com.appointment.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for TimeSlot entity.
 *
 * Design Pattern: Repository Pattern
 * - Abstracts time slot data access logic
 *
 * Design Pattern: Pessimistic Locking
 * - Uses database locks to prevent double-booking
 * - Critical for booking system integrity
 */
@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    /**
     * Find available time slots for a doctor on a specific date.
     * Used for displaying booking options to patients.
     *
     * @param doctorId Doctor's ID
     * @param date Date to check
     * @return List of available slots
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.doctor.id = :doctorId " + // Filter by doctor
            "AND ts.slotDate = :date " + // Filter by date
            "AND ts.isAvailable = true " + // Only available slots
            "ORDER BY ts.startTime") // Sort by time
    List<TimeSlot> findAvailableSlotsByDoctorAndDate(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Find time slot by ID with pessimistic write lock.
     * Prevents concurrent bookings of the same slot.
     *
     * Design Pattern: Pessimistic Locking Pattern
     * - Acquires database lock when reading the record
     * - Prevents other transactions from modifying until lock is released
     * - Essential for preventing double-booking
     *
     * Lock Flow:
     * 1. Transaction A locks slot for booking
     * 2. Transaction B tries to lock same slot, waits
     * 3. Transaction A completes booking, releases lock
     * 4. Transaction B acquires lock, finds slot unavailable, fails
     *
     * @param id Slot ID
     * @return Optional containing locked slot
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE) // Database row-level write lock
    // PESSIMISTIC_WRITE: Other transactions can read but not write
    @Query("SELECT ts FROM TimeSlot ts WHERE ts.id = :id")
    Optional<TimeSlot> findByIdWithLock(@Param("id") UUID id);

    /**
     * Find all slots for a doctor on a date range.
     * Used for doctor's schedule view.
     *
     * @param doctorId Doctor's ID
     * @param startDate Range start date
     * @param endDate Range end date
     * @return List of time slots in range
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.doctor.id = :doctorId " +
            "AND ts.slotDate BETWEEN :startDate AND :endDate " +
            "ORDER BY ts.slotDate, ts.startTime")
    List<TimeSlot> findSlotsByDoctorAndDateRange(
            @Param("doctorId") UUID doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Check if slot exists for doctor at specific date and time.
     * Used for validation when creating new slots.
     *
     * Design Pattern: Existence Check Pattern
     * - Uses boolean return type for efficient checking
     * - More performant than findBy + null check
     *
     * @param doctorId Doctor's ID
     * @param date Slot date
     * @param startTime Slot start time
     * @return true if slot exists
     */
    boolean existsByDoctorIdAndSlotDateAndStartTime(
            UUID doctorId,
            LocalDate date,
            LocalTime startTime
    );

    /**
     * Find overlapping slots for a doctor.
     * Used to prevent scheduling conflicts.
     *
     * Design Pattern: Range Overlap Detection
     * - Checks if new slot overlaps with existing slots
     * - Overlap condition: (start1 < end2) AND (start2 < end1)
     *
     * @param doctorId Doctor's ID
     * @param date Slot date
     * @param startTime New slot start time
     * @param endTime New slot end time
     * @return List of overlapping slots
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.doctor.id = :doctorId " +
            "AND ts.slotDate = :date " +
            "AND ts.startTime < :endTime " + // Overlap condition part 1
            "AND ts.endTime > :startTime") // Overlap condition part 2
    List<TimeSlot> findOverlappingSlots(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    /**
     * Find past slots that are still marked as available.
     * Used for cleanup jobs to mark past slots as unavailable.
     *
     * @param currentDate Current date
     * @return List of past available slots
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.slotDate < :currentDate " + // Past dates
            "AND ts.isAvailable = true") // Still marked available
    List<TimeSlot> findPastAvailableSlots(@Param("currentDate") LocalDate currentDate);

    /**
     * Find available slots for multiple doctors.
     * Used for bulk availability checking.
     *
     * @param doctorIds List of doctor IDs
     * @param date Date to check
     * @return List of available slots for all doctors
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.doctor.id IN :doctorIds " + // IN clause for multiple doctors
            "AND ts.slotDate = :date " +
            "AND ts.isAvailable = true " +
            "ORDER BY ts.doctor.id, ts.startTime")
    List<TimeSlot> findAvailableSlotsByDoctorsAndDate(
            @Param("doctorIds") List<UUID> doctorIds,
            @Param("date") LocalDate date
    );

    /**
     * Count available slots for a doctor on a date.
     * Used for quick availability check without loading all slots.
     *
     * @param doctorId Doctor's ID
     * @param date Date to check
     * @return Number of available slots
     */
    @Query("SELECT COUNT(ts) FROM TimeSlot ts " +
            "WHERE ts.doctor.id = :doctorId " +
            "AND ts.slotDate = :date " +
            "AND ts.isAvailable = true")
    long countAvailableSlots(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Find slots by doctor ordered by date and time.
     * Used for displaying doctor's complete schedule.
     *
     * @param doctorId Doctor's ID
     * @return List of all slots for doctor
     */
    List<TimeSlot> findByDoctorIdOrderBySlotDateAscStartTimeAsc(UUID doctorId);
    // OrderBy[Field]Asc: Generates ORDER BY clause with ASC

    /**
     * Find available slots starting from a specific date.
     * Used for "next available" feature.
     *
     * @param doctorId Doctor's ID
     * @param fromDate Starting date (inclusive)
     * @return List of future available slots
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.doctor.id = :doctorId " +
            "AND ts.slotDate >= :fromDate " + // Future dates
            "AND ts.isAvailable = true " +
            "ORDER BY ts.slotDate, ts.startTime")
    List<TimeSlot> findFutureAvailableSlots(
            @Param("doctorId") UUID doctorId,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Delete all slots for a doctor on a specific date.
     * Used when doctor cancels an entire day.
     *
     * Design Pattern: Bulk Delete Pattern
     * - Deletes multiple records matching criteria
     * - More efficient than deleting one by one
     *
     * @param doctorId Doctor's ID
     * @param date Date to clear
     */
    void deleteByDoctorIdAndSlotDate(UUID doctorId, LocalDate date);

    /**
     * Find slots with capacity for more bookings.
     * Used when maxBookings > 1 (group appointments).
     *
     * @param doctorId Doctor's ID
     * @param date Date to check
     * @return List of slots with available capacity
     */
    @Query("SELECT ts FROM TimeSlot ts " +
            "WHERE ts.doctor.id = :doctorId " +
            "AND ts.slotDate = :date " +
            "AND ts.currentBookings < ts.maxBookings " + // Has capacity
            "ORDER BY ts.startTime")
    List<TimeSlot> findSlotsWithCapacity(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date
    );
}
