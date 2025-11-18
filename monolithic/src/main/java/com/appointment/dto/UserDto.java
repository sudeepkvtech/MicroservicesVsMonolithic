package com.appointment.dto;

import com.appointment.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Data Transfer Object for User entity.
 *
 * Design Pattern: DTO (Data Transfer Object) Pattern
 * - Separates internal domain model from external API representation
 * - Prevents exposing sensitive fields (like passwordHash)
 * - Allows different views of same entity
 * - Reduces coupling between layers
 *
 * SOLID Principles:
 * - Single Responsibility: Only responsible for data transfer
 * - Open/Closed: Can create different DTOs for different purposes without modifying entity
 */
@Data // Lombok: Generates getters, setters, toString, equals, hashCode
@Builder // Lombok: Implements Builder pattern
@NoArgsConstructor // Required for JSON deserialization
@AllArgsConstructor // Required for Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Jackson: Only include non-null fields in JSON
// Reduces response size by excluding null values
public class UserDto {

    /**
     * User's unique identifier.
     * Included in responses, not required in create requests.
     */
    private UUID id;

    /**
     * User's first name.
     * Validation: Cannot be blank.
     */
    @NotBlank(message = "First name is required")
    private String firstName;

    /**
     * User's last name.
     * Validation: Cannot be blank.
     */
    @NotBlank(message = "Last name is required")
    private String lastName;

    /**
     * User's email address.
     * Validation: Must be valid email format.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /**
     * User's phone number.
     * Optional field.
     */
    private String phoneNumber;

    /**
     * User's date of birth.
     * Validation: Must be in the past.
     */
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    /**
     * User's address.
     * Optional field.
     */
    private String address;

    /**
     * User's roles in the system.
     * Used for authorization.
     */
    private Set<User.Role> roles;

    /**
     * Timestamp when user was created.
     * Read-only field, set by system.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when user was last updated.
     * Read-only field, set by system.
     */
    private LocalDateTime updatedAt;

    /**
     * Note: passwordHash is NOT included in DTO
     * Security: Prevents exposing password hash in API responses
     */

    /**
     * Nested DTO for user registration requests.
     *
     * Design Pattern: Builder Pattern
     * - Provides clean API for creating registration requests
     *
     * Why separate DTO?
     * - Registration requires password, but regular UserDto doesn't expose it
     * - Different validation rules for registration
     * - Keeps concerns separated
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        // Required fields for registration
        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        /**
         * Plain text password from user.
         * Will be hashed before storing in database.
         * Never stored or returned in plain text.
         */
        @NotBlank(message = "Password is required")
        // In production, add complexity requirements:
        // @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        //          message = "Password must be at least 8 characters with uppercase, lowercase, number, and special character")
        private String password;

        // Optional fields
        private String phoneNumber;
        private LocalDate dateOfBirth;
        private String address;
    }

    /**
     * Nested DTO for user login requests.
     *
     * Design Pattern: Command Pattern
     * - Encapsulates login action as an object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        // Only email and password needed for login
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    /**
     * Nested DTO for authentication response.
     *
     * Design Pattern: Response Object Pattern
     * - Encapsulates authentication result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        // JWT access token
        private String accessToken;

        // Token type (always "Bearer" for JWT)
        private String tokenType;

        // Token expiration time in seconds
        private Long expiresIn;

        // User information
        private UserDto user;
    }

    /**
     * Nested DTO for updating user profile.
     *
     * Why separate from main DTO?
     * - Update doesn't include all fields
     * - Some fields are immutable (email might require verification)
     * - Different validation rules
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        // Fields that can be updated
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private LocalDate dateOfBirth;
        private String address;

        // Email and roles NOT included (require separate processes)
    }
}
