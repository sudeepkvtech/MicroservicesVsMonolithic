package com.appointment.repository;

import com.appointment.model.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Doctor entity.
 *
 * Design Pattern: Repository Pattern
 * - Encapsulates data access logic for Doctor entity
 * - Provides abstraction over database operations
 *
 * Design Pattern: Pagination Pattern
 * - Uses Spring Data's Page and Pageable for efficient large dataset handling
 * - Prevents loading all records into memory
 */
@Repository // Marks this as a Spring Data repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    /**
     * Find doctor by email.
     * Used for doctor login and lookup.
     *
     * @param email Doctor's email
     * @return Optional containing doctor if found
     */
    Optional<Doctor> findByEmail(String email);

    /**
     * Check if doctor exists with given email.
     * Used for validation during registration.
     *
     * @param email Email to check
     * @return true if doctor exists
     */
    boolean existsByEmail(String email);

    /**
     * Find doctors by specialization.
     * Used for patient search and filtering.
     *
     * Design Pattern: Query Method Pattern
     * - Method name automatically generates query
     * - Case-insensitive search using IgnoreCase
     *
     * @param specialization Medical specialization
     * @return List of doctors with matching specialization
     */
    List<Doctor> findBySpecializationIgnoreCase(String specialization);
    // IgnoreCase: Generates LOWER(specialization) = LOWER(?) in SQL

    /**
     * Find doctors by specialization with pagination.
     * Used for search results with large datasets.
     *
     * Design Pattern: Pagination Pattern
     * - Returns Page object with pagination metadata
     * - Efficient for large result sets
     *
     * @param specialization Medical specialization
     * @param pageable Pagination parameters (page number, size, sort)
     * @return Page of doctors
     *
     * Example usage:
     * Pageable pageable = PageRequest.of(0, 10, Sort.by("firstName"));
     * Page<Doctor> doctors = repository.findBySpecialization("Cardiology", pageable);
     */
    Page<Doctor> findBySpecializationIgnoreCase(String specialization, Pageable pageable);

    /**
     * Find doctors accepting new patients.
     * Used to filter out doctors not available for booking.
     *
     * @return List of doctors accepting patients
     */
    List<Doctor> findByIsAcceptingPatientsTrue();

    /**
     * Find doctors by specialization who are accepting patients.
     * Combines multiple filters.
     *
     * Design Pattern: Query Method Pattern with Multiple Conditions
     * - "And" keyword combines conditions
     * - Generates WHERE clause with AND operator
     *
     * @param specialization Medical specialization
     * @return List of available doctors in specialization
     */
    List<Doctor> findBySpecializationIgnoreCaseAndIsAcceptingPatientsTrue(String specialization);

    /**
     * Find doctors with consultation fee within range.
     * Used for price-based filtering.
     *
     * Design Pattern: Range Query Pattern
     * - Between keyword generates BETWEEN SQL clause
     *
     * @param minFee Minimum consultation fee
     * @param maxFee Maximum consultation fee
     * @return List of doctors within price range
     */
    List<Doctor> findByConsultationFeeBetween(BigDecimal minFee, BigDecimal maxFee);

    /**
     * Find doctors with minimum years of experience.
     * Used for filtering by experience level.
     *
     * @param years Minimum years of experience
     * @return List of experienced doctors
     */
    List<Doctor> findByExperienceYearsGreaterThanEqual(Integer years);
    // GreaterThanEqual: Generates >= in SQL

    /**
     * Search doctors by name (first or last name contains search term).
     * Used for doctor search functionality.
     *
     * Design Pattern: Custom Query with OR Conditions
     * - Uses @Query for complex search logic
     * - LIKE operator for partial matching
     *
     * @param searchTerm Search keyword
     * @return List of doctors matching search term
     */
    @Query("SELECT d FROM Doctor d WHERE " +
            "LOWER(d.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            // CONCAT('%', term, '%'): Adds wildcards for partial match
            "LOWER(d.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Doctor> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find doctors with available slots on a specific date.
     * Used for availability-based search.
     *
     * Design Pattern: Join Query Pattern
     * - Joins Doctor with TimeSlot entity
     * - Filters by date and availability
     *
     * @param date Date to check availability
     * @return List of doctors with available slots
     */
    @Query("SELECT DISTINCT d FROM Doctor d " +
            "JOIN d.timeSlots ts " + // Join with time slots
            "WHERE ts.slotDate = :date " + // Filter by date
            "AND ts.isAvailable = true") // Only available slots
    List<Doctor> findDoctorsWithAvailableSlotsOnDate(@Param("date") LocalDate date);
    // DISTINCT: Prevents duplicate doctors with multiple slots

    /**
     * Find doctors by specialization with available slots on date.
     * Combines specialization filter with availability check.
     *
     * Design Pattern: Complex Query Pattern
     * - Multiple JOIN and WHERE conditions
     * - Demonstrates power of JPQL
     *
     * @param specialization Medical specialization
     * @param date Date to check
     * @return List of available doctors in specialization
     */
    @Query("SELECT DISTINCT d FROM Doctor d " +
            "JOIN d.timeSlots ts " +
            "WHERE LOWER(d.specialization) = LOWER(:specialization) " +
            "AND ts.slotDate = :date " +
            "AND ts.isAvailable = true " +
            "AND d.isAcceptingPatients = true")
    List<Doctor> findAvailableDoctorsBySpecializationAndDate(
            @Param("specialization") String specialization,
            @Param("date") LocalDate date
    );

    /**
     * Get doctor with all time slots eagerly loaded.
     * Prevents N+1 query problem when accessing slots.
     *
     * Design Pattern: Eager Loading Pattern
     * - Uses JOIN FETCH to load associations in one query
     *
     * @param id Doctor's ID
     * @return Optional containing doctor with slots
     */
    @Query("SELECT d FROM Doctor d LEFT JOIN FETCH d.timeSlots WHERE d.id = :id")
    Optional<Doctor> findByIdWithTimeSlots(@Param("id") UUID id);

    /**
     * Find top doctors by number of completed appointments.
     * Used for "Popular Doctors" feature.
     *
     * Design Pattern: Aggregation Query Pattern
     * - Uses COUNT and GROUP BY for statistics
     * - Orders by count in descending order
     *
     * @param pageable Pagination parameters (for limiting results)
     * @return Page of top doctors
     */
    @Query("SELECT d FROM Doctor d " +
            "LEFT JOIN d.appointments a " +
            "WHERE a.status = 'COMPLETED' " + // Only count completed appointments
            "GROUP BY d " + // Group by doctor
            "ORDER BY COUNT(a) DESC") // Order by appointment count
    Page<Doctor> findTopDoctors(Pageable pageable);

    /**
     * Count doctors by specialization.
     * Used for analytics and statistics.
     *
     * @param specialization Specialization to count
     * @return Number of doctors in specialization
     */
    long countBySpecialization(String specialization);

    /**
     * Find all distinct specializations.
     * Used for filter dropdowns and autocomplete.
     *
     * Design Pattern: Distinct Values Query
     * - Returns unique list of specializations
     *
     * @return List of all specializations in system
     */
    @Query("SELECT DISTINCT d.specialization FROM Doctor d ORDER BY d.specialization")
    List<String> findAllSpecializations();

    /**
     * Advanced search with multiple optional filters.
     * Demonstrates dynamic query construction.
     *
     * @param specialization Optional specialization filter
     * @param minFee Optional minimum fee
     * @param maxFee Optional maximum fee
     * @param minExperience Optional minimum experience
     * @param pageable Pagination parameters
     * @return Page of doctors matching filters
     */
    @Query("SELECT d FROM Doctor d WHERE " +
            "(:specialization IS NULL OR LOWER(d.specialization) = LOWER(:specialization)) AND " +
            // :param IS NULL: Ignores filter if param is null
            "(:minFee IS NULL OR d.consultationFee >= :minFee) AND " +
            "(:maxFee IS NULL OR d.consultationFee <= :maxFee) AND " +
            "(:minExperience IS NULL OR d.experienceYears >= :minExperience) AND " +
            "d.isAcceptingPatients = true")
    Page<Doctor> searchDoctors(
            @Param("specialization") String specialization,
            @Param("minFee") BigDecimal minFee,
            @Param("maxFee") BigDecimal maxFee,
            @Param("minExperience") Integer minExperience,
            Pageable pageable
    );
}
