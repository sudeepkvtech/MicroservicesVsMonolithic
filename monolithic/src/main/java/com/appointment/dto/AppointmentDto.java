package com.appointment.dto;

import com.appointment.model.Appointment;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Appointment entity.
 *
 * Design Pattern: DTO Pattern
 * - Provides different views of appointment data
 * - Separates API contract from domain model
 * - Can include computed/derived fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppointmentDto {

    // Appointment ID
    private UUID id;

    // Patient information (nested DTO)
    private PatientInfo patient;

    // Doctor information (nested DTO)
    private DoctorInfo doctor;

    // Time slot information (nested DTO)
    private TimeSlotInfo timeSlot;

    // Appointment status
    private Appointment.AppointmentStatus status;

    // Patient's reason for visit
    private String reasonForVisit;

    // Doctor's notes (only visible after appointment)
    private String doctorNotes;

    // Prescription (only visible after appointment)
    private String prescription;

    // Payment information (if exists)
    private PaymentDto payment;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Nested DTO for patient information in appointment.
     *
     * Design Pattern: Nested DTO Pattern
     * - Includes only relevant patient fields for appointment view
     * - Avoids sending full user object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientInfo {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;

        // Method to get full name
        public String getFullName() {
            return firstName + " " + lastName;
        }
    }

    /**
     * Nested DTO for doctor information in appointment.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorInfo {
        private UUID id;
        private String firstName;
        private String lastName;
        private String specialization;
        private String email;
        private String phoneNumber;

        public String getFullName() {
            return "Dr. " + firstName + " " + lastName;
        }
    }

    /**
     * Nested DTO for time slot information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlotInfo {
        private UUID id;
        private java.time.LocalDate date;
        private java.time.LocalTime startTime;
        private java.time.LocalTime endTime;
    }

    /**
     * DTO for creating new appointment.
     *
     * Design Pattern: Command Pattern
     * - Represents the "book appointment" command
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookRequest {
        // IDs of related entities
        @NotNull(message = "Doctor ID is required")
        private UUID doctorId;

        @NotNull(message = "Time slot ID is required")
        private UUID timeSlotId;

        // Optional reason for visit
        private String reasonForVisit;

        // Note: Patient ID comes from authenticated user
    }

    /**
     * DTO for rescheduling appointment.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RescheduleRequest {
        @NotNull(message = "New time slot ID is required")
        private UUID newTimeSlotId;

        private String reason; // Optional reason for rescheduling
    }

    /**
     * DTO for cancelling appointment.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelRequest {
        private String reason; // Optional cancellation reason
    }

    /**
     * DTO for adding doctor's notes.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddNotesRequest {
        @NotNull(message = "Doctor notes are required")
        private String notes;

        private String prescription; // Optional prescription
    }
}
