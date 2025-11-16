package com.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main entry point for the Monolithic Appointment Scheduling Application.
 *
 * Design Pattern: Follows Spring Boot's Convention over Configuration principle
 * This class bootstraps the entire application with a single annotation.
 *
 * Annotations explained:
 * - @SpringBootApplication: Combines @Configuration, @EnableAutoConfiguration, and @ComponentScan
 * - @EnableJpaAuditing: Enables JPA auditing for automatic createdAt/updatedAt fields
 * - @EnableAsync: Enables asynchronous method execution (for notifications)
 * - @EnableTransactionManagement: Enables declarative transaction management
 */
@SpringBootApplication // Marks this as the main Spring Boot application class
@EnableJpaAuditing // Enables automatic auditing of entity timestamps
@EnableAsync // Enables @Async annotation for asynchronous processing
@EnableTransactionManagement // Enables @Transactional annotation for database transactions
public class AppointmentMonolithicApplication {

    /**
     * Main method that starts the Spring Boot application.
     *
     * @param args Command-line arguments passed to the application
     */
    public static void main(String[] args) {
        // SpringApplication.run() boots up the Spring application context
        // It scans for components, configures beans, and starts the embedded web server
        SpringApplication.run(AppointmentMonolithicApplication.class, args);
    }
}
