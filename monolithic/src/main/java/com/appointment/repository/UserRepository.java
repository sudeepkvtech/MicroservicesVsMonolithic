package com.appointment.repository;

import com.appointment.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity.
 *
 * Design Pattern: Repository Pattern
 * - Abstracts data access logic from business logic
 * - Provides clean separation between domain and data layers
 * - Makes code more testable (can mock repository)
 *
 * Design Pattern: Proxy Pattern (Spring Data JPA)
 * - Spring creates proxy implementation at runtime
 * - No need to write boilerplate CRUD code
 *
 * SOLID Principles:
 * - Interface Segregation: Clients depend on specific interface, not implementation
 * - Dependency Inversion: High-level code depends on abstraction (interface)
 */
@Repository // Spring annotation: Marks this as a repository component
// JpaRepository<User, UUID>: Provides CRUD operations for User entity with UUID primary key
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email address.
     * Used for authentication and user lookup.
     *
     * Design Pattern: Query Method Pattern (Spring Data JPA)
     * - Method name is parsed to generate SQL query
     * - "findBy" + "Email" -> SELECT * FROM users WHERE email = ?
     *
     * @param email User's email address
     * @return Optional containing user if found, empty otherwise
     *
     * Why Optional?
     * - Avoids NullPointerException
     * - Forces caller to handle "not found" case
     * - More functional programming approach
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists with given email.
     * Used for validation during registration.
     *
     * Design Pattern: Query Method Pattern
     * - "existsBy" generates COUNT query for efficiency
     * - More efficient than findBy + check for null
     *
     * @param email Email to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find user by email with roles eagerly loaded.
     * Used when we need to check user permissions immediately.
     *
     * Design Pattern: Custom Query Pattern
     * - Uses @Query annotation for complex queries
     * - JOIN FETCH eagerly loads roles to avoid N+1 query problem
     *
     * N+1 Problem:
     * - Without JOIN FETCH: 1 query for user + N queries for roles
     * - With JOIN FETCH: 1 query for both user and roles
     *
     * @param email User's email
     * @return Optional containing user with roles loaded
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    // JPQL (Java Persistence Query Language) query
    // LEFT JOIN FETCH: Loads roles in same query (eager loading)
    Optional<User> findByEmailWithRoles(@Param("email") String email);
    // @Param: Binds method parameter to query parameter

    /**
     * Find user by phone number.
     * Used for alternate lookup method.
     *
     * @param phoneNumber User's phone number
     * @return Optional containing user if found
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Find all users with a specific role.
     * Used for admin queries and reporting.
     *
     * Design Pattern: Query Method with Collection
     * - Spring Data JPA handles collection membership queries
     *
     * @param role Role to search for
     * @return List of users with the specified role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    // JOIN: Joins user_roles table
    // WHERE r = :role: Filters by role value
    java.util.List<User> findByRole(@Param("role") User.Role role);

    /**
     * Custom method to find users with upcoming appointments.
     * Demonstrates complex query with joins and date filtering.
     *
     * @return List of users who have future appointments
     */
    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN u.appointments a " + // Join with appointments
            "JOIN a.timeSlot ts " + // Join with time slots
            "WHERE ts.slotDate >= CURRENT_DATE " + // Future dates only
            "AND a.status IN ('PENDING', 'CONFIRMED')") // Active appointments only
    java.util.List<User> findUsersWithUpcomingAppointments();
    // DISTINCT: Prevents duplicate users if they have multiple appointments

    /**
     * Count users by role.
     * Used for analytics and dashboards.
     *
     * @param role Role to count
     * @return Number of users with the role
     */
    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r = :role")
    long countByRole(@Param("role") User.Role role);

    /**
     * Inherited methods from JpaRepository (no need to implement):
     *
     * save(User user) - Insert or update user
     * findById(UUID id) - Find user by ID
     * findAll() - Get all users
     * deleteById(UUID id) - Delete user by ID
     * count() - Count total users
     * existsById(UUID id) - Check if user exists
     *
     * And many more...
     */
}
