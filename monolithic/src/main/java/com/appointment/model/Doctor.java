package com.appointment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Doctor Entity - Represents a doctor in the system.
 *
 * Design Pattern: Entity Pattern (DDD)
 * - Core domain object with identity and lifecycle
 * - Encapsulates doctor-specific business logic
 *
 * Design Pattern: Value Object Pattern (for consultation fee)
 * - Uses BigDecimal for precise monetary calculations
 *
 * SOLID Principles:
 * - Single Responsibility: Manages doctor data and relationships
 * - Encapsulation: Business logic contained within the entity
 */
@Entity // JPA entity annotation
@Table(name = "doctors", // Database table name
        indexes = {
                @Index(name = "idx_doctor_specialization", columnList = "specialization"), // For faster searches
                @Index(name = "idx_doctor_email", columnList = "email") // For login lookups
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_doctor_email", columnNames = "email") // Email uniqueness
        })
@Getter // Lombok: Generate getters
@Setter // Lombok: Generate setters
@NoArgsConstructor // Lombok: No-args constructor for JPA
@AllArgsConstructor // Lombok: All-args constructor
@Builder // Lombok: Builder pattern implementation
public class Doctor extends BaseEntity {

    /**
     * Doctor's first name.
     * Required field with length validation.
     */
    @NotBlank(message = "First name is required") // Validation: Cannot be blank
    @Column(name = "first_name", nullable = false, length = 100) // DB column config
    private String firstName;

    /**
     * Doctor's last name.
     * Required field with length validation.
     */
    @NotBlank(message = "Last name is required") // Must have content
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Doctor's email address.
     * Used for communication and potentially login.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid") // Email format validation
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Doctor's phone number.
     * Optional but validated if provided.
     */
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", // E.164 format
            message = "Phone number must be valid")
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * Doctor's medical specialization.
     * Examples: Cardiology, Dermatology, Pediatrics, etc.
     * Used for filtering and searching doctors.
     */
    @NotBlank(message = "Specialization is required")
    @Column(name = "specialization", nullable = false, length = 100)
    private String specialization;

    /**
     * Doctor's qualifications and degrees.
     * Examples: MBBS, MD, MS, etc.
     * Displayed to patients for credibility.
     */
    @Column(name = "qualifications", length = 500)
    private String qualifications;

    /**
     * Years of medical experience.
     * Used for filtering and patient decision-making.
     * Must be non-negative.
     */
    @Min(value = 0, message = "Experience cannot be negative") // Cannot be less than 0
    @Column(name = "experience_years")
    private Integer experienceYears;

    /**
     * Consultation fee charged by the doctor.
     * Uses BigDecimal for precise decimal arithmetic (important for money).
     *
     * Why BigDecimal over Double:
     * - Prevents floating-point precision errors
     * - Exact decimal representation
     * - Essential for financial calculations
     */
    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.0", inclusive = false, // Must be greater than 0
            message = "Consultation fee must be greater than zero")
    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    // precision=10: total digits, scale=2: decimal places (e.g., 99999999.99)
    private BigDecimal consultationFee;

    /**
     * Hospital or clinic ID where the doctor practices.
     * Optional: Doctor might have private practice.
     * Future enhancement: Can be converted to ManyToOne relationship with Hospital entity.
     */
    @Column(name = "hospital_id", length = 100)
    private String hospitalId;

    /**
     * Doctor's biography or description.
     * Provides additional information to patients.
     */
    @Column(name = "bio", columnDefinition = "TEXT") // TEXT type for longer content
    private String bio;

    /**
     * Indicates if the doctor is currently accepting new patients.
     * Business logic flag to control appointment bookings.
     */
    @Column(name = "is_accepting_patients", nullable = false)
    @Builder.Default // Default value when using builder
    private Boolean isAcceptingPatients = true;

    /**
     * Relationship: One doctor can have many time slots.
     *
     * Design Pattern: One-to-Many Relationship
     * - Lazy loading for performance
     * - Cascade all operations to time slots
     * - Orphan removal: Delete slots if removed from collection
     */
    @OneToMany(mappedBy = "doctor", // References 'doctor' field in TimeSlot
            cascade = CascadeType.ALL, // Cascade persist, merge, remove, etc.
            orphanRemoval = true, // Remove orphaned time slots
            fetch = FetchType.LAZY) // Load slots only when accessed
    @Builder.Default // Initialize to prevent null
    private Set<TimeSlot> timeSlots = new HashSet<>();

    /**
     * Relationship: One doctor can have many appointments.
     *
     * Design Pattern: One-to-Many Relationship
     * - Bidirectional relationship with Appointment
     */
    @OneToMany(mappedBy = "doctor", // References 'doctor' field in Appointment
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}, // Don't cascade remove (keep appointment history)
            fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Appointment> appointments = new HashSet<>();

    /**
     * Business method to add a time slot to the doctor's availability.
     * Maintains bidirectional relationship consistency.
     *
     * @param timeSlot The time slot to add
     */
    public void addTimeSlot(TimeSlot timeSlot) {
        // Add slot to this doctor's collection
        this.timeSlots.add(timeSlot);
        // Set the doctor reference in the slot (bidirectional consistency)
        timeSlot.setDoctor(this);
    }

    /**
     * Business method to remove a time slot.
     * Maintains bidirectional relationship consistency.
     *
     * @param timeSlot The time slot to remove
     */
    public void removeTimeSlot(TimeSlot timeSlot) {
        // Remove from collection
        this.timeSlots.remove(timeSlot);
        // Clear the doctor reference
        timeSlot.setDoctor(null);
    }

    /**
     * Business method to get doctor's full name.
     * Encapsulates name formatting logic.
     *
     * @return Full name with title
     */
    public String getFullName() {
        // Prefix with 'Dr.' title
        return "Dr. " + firstName + " " + lastName;
    }

    /**
     * Business method to check if doctor has available slots.
     * Used for filtering search results.
     *
     * @return true if doctor has at least one available slot
     */
    public boolean hasAvailableSlots() {
        // Stream through time slots and check if any is available
        return timeSlots.stream()
                .anyMatch(TimeSlot::getIsAvailable); // Method reference
    }
}
