package com.appointment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * User Entity - Represents a patient in the system.
 *
 * Design Pattern: Entity Pattern (Domain-Driven Design)
 * - Represents a core domain object with identity
 * - Encapsulates business logic related to users
 *
 * Design Pattern: Builder Pattern (via Lombok @Builder)
 * - Provides fluent API for object construction
 * - Makes object creation more readable
 *
 * SOLID Principles:
 * - Single Responsibility: Represents user data and basic user operations
 * - Encapsulation: Private fields with controlled access
 */
@Entity // Marks this class as a JPA entity (maps to database table)
@Table(name = "users", // Specifies the table name in the database
        indexes = {
                @Index(name = "idx_user_email", columnList = "email"), // Index for faster email lookups
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_email", columnNames = "email") // Ensures email uniqueness
        })
@Getter // Lombok: Generates getter methods
@Setter // Lombok: Generates setter methods
@NoArgsConstructor // Lombok: Generates no-argument constructor (required by JPA)
@AllArgsConstructor // Lombok: Generates constructor with all arguments
@Builder // Lombok: Implements Builder pattern for object creation
public class User extends BaseEntity {

    /**
     * User's first name.
     * Validation: Cannot be blank, must contain only letters and spaces.
     */
    @NotBlank(message = "First name is required") // JSR-303 validation: Field cannot be null or empty
    @Column(name = "first_name", nullable = false, length = 100) // Database column: required, max 100 chars
    private String firstName;

    /**
     * User's last name.
     * Validation: Cannot be blank, must contain only letters and spaces.
     */
    @NotBlank(message = "Last name is required") // Field must have non-whitespace content
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * User's email address - used for login and communications.
     * Validation: Must be valid email format, unique across all users.
     */
    @NotBlank(message = "Email is required") // Email cannot be empty
    @Email(message = "Email must be valid") // Validates email format using regex
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * User's hashed password.
     * Security: Password is hashed using BCrypt before storage (never stored as plain text).
     * This field stores only the hash, not the actual password.
     */
    @NotBlank(message = "Password is required") // Password hash cannot be empty
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * User's phone number.
     * Validation: Optional, but if provided must match phone number pattern.
     */
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", // E.164 international phone number format
            message = "Phone number must be valid") // Validation error message
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * User's date of birth.
     * Validation: Must be a past date (cannot be born in the future).
     * Used for age verification and patient records.
     */
    @Past(message = "Date of birth must be in the past") // Validates date is before current date
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * User's address for location-based services and communications.
     * Optional field, can be null.
     */
    @Column(name = "address", length = 500) // Allows up to 500 characters
    private String address;

    /**
     * User's role(s) in the system.
     * Defines what actions the user can perform (authorization).
     *
     * Design Pattern: Set Collection
     * - Prevents duplicate roles
     * - Unordered collection suitable for role checking
     */
    @ElementCollection(fetch = FetchType.EAGER) // Eagerly loads roles with user (needed for auth)
    @CollectionTable(name = "user_roles", // Creates separate table for roles
            joinColumns = @JoinColumn(name = "user_id")) // Foreign key to users table
    @Enumerated(EnumType.STRING) // Stores enum as string in database (more readable)
    @Column(name = "role") // Column name in user_roles table
    @Builder.Default // Sets default value when using builder pattern
    private Set<Role> roles = new HashSet<>(); // Initialize to prevent NullPointerException

    /**
     * Relationship: One user can have many appointments.
     *
     * Design Pattern: One-to-Many Relationship
     * - Bidirectional relationship with Appointment entity
     * - Lazy loading: Appointments loaded only when accessed (performance optimization)
     * - Cascade operations: When user is deleted, appointments are also deleted
     */
    @OneToMany(mappedBy = "patient", // Refers to 'patient' field in Appointment entity
            cascade = CascadeType.ALL, // All JPA operations cascade to appointments
            orphanRemoval = true, // Remove appointments if removed from this collection
            fetch = FetchType.LAZY) // Don't load appointments unless explicitly accessed
    @Builder.Default // Initialize collection to prevent NullPointerException
    private Set<Appointment> appointments = new HashSet<>();

    /**
     * Business method to add a role to the user.
     * Encapsulates the logic of role management.
     *
     * @param role The role to add
     */
    public void addRole(Role role) {
        // Add role to the set (Set prevents duplicates automatically)
        this.roles.add(role);
    }

    /**
     * Business method to remove a role from the user.
     *
     * @param role The role to remove
     */
    public void removeRole(Role role) {
        // Remove role from the set
        this.roles.remove(role);
    }

    /**
     * Business method to check if user has a specific role.
     * Used for authorization checks.
     *
     * @param role The role to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(Role role) {
        // Check if role exists in the user's roles set
        return this.roles.contains(role);
    }

    /**
     * Business method to get user's full name.
     * Encapsulates the logic of name formatting.
     *
     * @return Full name (firstName + lastName)
     */
    public String getFullName() {
        // Concatenate first and last name with space
        return firstName + " " + lastName;
    }

    /**
     * Enumeration of possible user roles in the system.
     *
     * Design Pattern: Type-Safe Enumeration
     * - Prevents invalid role values
     * - Provides compile-time type safety
     * - Easier to maintain than string constants
     */
    public enum Role {
        ROLE_PATIENT,  // Regular patient user
        ROLE_DOCTOR,   // Doctor user (can manage availability)
        ROLE_ADMIN     // Admin user (full system access)
    }
}
