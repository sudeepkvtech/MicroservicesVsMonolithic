package com.appointment.service;

import com.appointment.dto.AppointmentDto;
import com.appointment.model.Appointment;
import com.appointment.model.TimeSlot;
import com.appointment.model.User;
import com.appointment.repository.AppointmentRepository;
import com.appointment.repository.TimeSlotRepository;
import com.appointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service class for appointment business logic.
 *
 * Design Patterns Used:
 * 1. Service Layer Pattern - Encapsulates business logic
 * 2. Transaction Script Pattern - Each method is a transaction
 * 3. Dependency Injection Pattern - Dependencies injected via constructor
 *
 * SOLID Principles:
 * 1. Single Responsibility - Only handles appointment business logic
 * 2. Open/Closed - Open for extension (can be extended), closed for modification
 * 3. Liskov Substitution - Can be replaced with mock for testing
 * 4. Interface Segregation - Focused interface (could extract to interface)
 * 5. Dependency Inversion - Depends on abstractions (Repository interfaces)
 */
@Service // Spring annotation: Marks this as a service component
@RequiredArgsConstructor // Lombok: Generates constructor with required (final) fields
// This implements Constructor Injection (best practice for DI)
@Slf4j // Lombok: Adds logger field (log)
@Transactional // All methods run in database transactions (ACID properties)
// Ensures data consistency: Either all operations succeed or all rollback
public class AppointmentService {

    // Dependencies injected via constructor (Dependency Injection Pattern)
    // Using final ensures immutability (thread-safe, can't be reassigned)
    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    // In real application, would also inject:
    // - DoctorRepository
    // - NotificationService (to send confirmations)
    // - PaymentService (to handle payments)

    /**
     * Book a new appointment.
     *
     * Design Pattern: Facade Pattern
     * - Provides simple interface for complex subsystem
     * - Coordinates multiple repositories and business rules
     *
     * Business Rules:
     * 1. Patient must exist
     * 2. Time slot must be available
     * 3. No double booking allowed
     * 4. Slot is locked during booking (prevents race conditions)
     *
     * @param patientId Patient's ID
     * @param request Booking request
     * @return Created appointment DTO
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if business rule violated
     */
    public AppointmentDto bookAppointment(UUID patientId, AppointmentDto.BookRequest request) {
        // Log method entry for debugging and monitoring
        log.info("Booking appointment for patient: {}, slot: {}",
                patientId, request.getTimeSlotId());

        // Step 1: Validate patient exists
        // Use orElseThrow to fail fast if patient not found
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Patient not found with ID: " + patientId));

        // Step 2: Lock and retrieve time slot (prevents double booking)
        // Design Pattern: Pessimistic Locking
        // Lock ensures only one transaction can book this slot at a time
        TimeSlot timeSlot = timeSlotRepository.findByIdWithLock(request.getTimeSlotId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Time slot not found with ID: " + request.getTimeSlotId()));

        // Step 3: Validate business rules
        validateAppointmentBooking(patient, timeSlot);

        // Step 4: Mark slot as booked
        // This method encapsulates booking logic and throws exception if fails
        try {
            timeSlot.book(); // Marks slot as unavailable
        } catch (IllegalStateException e) {
            // Slot was already booked by another transaction
            log.error("Failed to book slot {}: {}", timeSlot.getId(), e.getMessage());
            throw new IllegalStateException("Time slot is no longer available", e);
        }

        // Step 5: Create appointment entity
        Appointment appointment = Appointment.builder()
                .patient(patient) // Set patient
                .doctor(timeSlot.getDoctor()) // Get doctor from time slot
                .timeSlot(timeSlot) // Set time slot
                .status(Appointment.AppointmentStatus.PENDING) // Initial status
                .reasonForVisit(request.getReasonForVisit()) // Optional reason
                .build();

        // Step 6: Save appointment to database
        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Step 7: Log successful booking
        log.info("Successfully booked appointment {}", savedAppointment.getId());

        // TODO: In production, add these:
        // - Create payment record (paymentService.createPayment(appointment))
        // - Send confirmation notification (notificationService.sendConfirmation(appointment))
        // - Publish AppointmentBooked event (eventPublisher.publish(event))

        // Step 8: Convert entity to DTO and return
        // Design Pattern: Mapping Pattern
        return convertToDto(savedAppointment);
    }

    /**
     * Validate appointment booking business rules.
     *
     * Design Pattern: Validation Pattern
     * - Separates validation logic into its own method
     * - Follows Single Responsibility Principle
     * - Reusable validation logic
     *
     * @param patient Patient booking appointment
     * @param timeSlot Time slot being booked
     * @throws IllegalStateException if validation fails
     */
    private void validateAppointmentBooking(User patient, TimeSlot timeSlot) {
        // Rule 1: Time slot must be in the future
        if (timeSlot.isPast()) {
            throw new IllegalStateException("Cannot book appointment in the past");
        }

        // Rule 2: Time slot must be available
        if (!timeSlot.getIsAvailable()) {
            throw new IllegalStateException("Time slot is not available");
        }

        // Rule 3: Patient cannot have multiple appointments with same doctor on same day
        // Business rule to prevent duplicate bookings
        boolean hasExistingAppointment = appointmentRepository
                .existsActiveAppointmentForPatientWithDoctorOnDate(
                        patient.getId(),
                        timeSlot.getDoctor().getId(),
                        timeSlot.getSlotDate()
                );

        if (hasExistingAppointment) {
            throw new IllegalStateException(
                    "You already have an appointment with this doctor on this date");
        }

        // Additional business rules can be added here:
        // - Check patient eligibility
        // - Check doctor availability
        // - Check insurance coverage
        // etc.
    }

    /**
     * Get appointment by ID.
     *
     * Design Pattern: Repository Pattern
     * - Delegates data access to repository
     * - Service adds business logic layer
     *
     * @param appointmentId Appointment ID
     * @return Appointment DTO
     * @throws IllegalArgumentException if not found
     */
    @Transactional(readOnly = true) // Optimization: Read-only transaction
    // readOnly=true allows database to optimize for read operations
    public AppointmentDto getAppointmentById(UUID appointmentId) {
        // Log the request
        log.debug("Fetching appointment: {}", appointmentId);

        // Fetch appointment with all details in one query (eager loading)
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with ID: " + appointmentId));

        // Convert and return
        return convertToDto(appointment);
    }

    /**
     * Get all appointments for a patient.
     *
     * @param patientId Patient's ID
     * @return List of appointment DTOs
     */
    @Transactional(readOnly = true)
    public List<AppointmentDto> getPatientAppointments(UUID patientId) {
        log.debug("Fetching appointments for patient: {}", patientId);

        // Fetch appointments from repository
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);

        // Convert list of entities to list of DTOs
        // Design Pattern: Stream API for functional programming
        return appointments.stream()
                .map(this::convertToDto) // Method reference to conversion method
                .collect(Collectors.toList()); // Collect results to list
    }

    /**
     * Get upcoming appointments for a patient.
     *
     * @param patientId Patient's ID
     * @return List of upcoming appointments
     */
    @Transactional(readOnly = true)
    public List<AppointmentDto> getUpcomingAppointments(UUID patientId) {
        log.debug("Fetching upcoming appointments for patient: {}", patientId);

        // Get today's date
        LocalDate today = LocalDate.now();

        // Fetch upcoming appointments
        List<Appointment> appointments = appointmentRepository
                .findUpcomingAppointmentsByPatient(patientId, today);

        // Convert and return
        return appointments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Cancel an appointment.
     *
     * Business Rules:
     * 1. Appointment must exist
     * 2. Appointment must be cancellable (not already completed)
     * 3. Time slot is freed up
     * 4. Payment is refunded (if applicable)
     *
     * @param appointmentId Appointment ID
     * @param patientId Patient ID (for authorization)
     * @throws IllegalArgumentException if not found
     * @throws IllegalStateException if cannot be cancelled
     */
    public void cancelAppointment(UUID appointmentId, UUID patientId) {
        log.info("Cancelling appointment: {} for patient: {}", appointmentId, patientId);

        // Step 1: Fetch appointment
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with ID: " + appointmentId));

        // Step 2: Authorization check
        // Ensure patient can only cancel their own appointments
        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new IllegalStateException(
                    "You are not authorized to cancel this appointment");
        }

        // Step 3: Cancel appointment (business logic in entity)
        // This method validates state transitions and updates slot
        try {
            appointment.cancel();
        } catch (IllegalStateException e) {
            log.error("Failed to cancel appointment {}: {}", appointmentId, e.getMessage());
            throw e;
        }

        // Step 4: Save changes
        appointmentRepository.save(appointment);

        // Step 5: Log success
        log.info("Successfully cancelled appointment: {}", appointmentId);

        // TODO: In production:
        // - Process refund (paymentService.refund(appointment.getPayment()))
        // - Send cancellation notification (notificationService.sendCancellation())
        // - Publish AppointmentCancelled event
    }

    /**
     * Convert Appointment entity to DTO.
     *
     * Design Pattern: Mapper Pattern
     * - Separates entity-to-DTO conversion logic
     * - In production, use MapStruct for automatic mapping
     *
     * Why manual mapping here?
     * - Demonstrates the pattern clearly
     * - Full control over what data is exposed
     * - Can add computed fields
     *
     * @param appointment Appointment entity
     * @return Appointment DTO
     */
    private AppointmentDto convertToDto(Appointment appointment) {
        // Build patient info DTO
        AppointmentDto.PatientInfo patientInfo = AppointmentDto.PatientInfo.builder()
                .id(appointment.getPatient().getId())
                .firstName(appointment.getPatient().getFirstName())
                .lastName(appointment.getPatient().getLastName())
                .email(appointment.getPatient().getEmail())
                .phoneNumber(appointment.getPatient().getPhoneNumber())
                .build();

        // Build doctor info DTO
        AppointmentDto.DoctorInfo doctorInfo = AppointmentDto.DoctorInfo.builder()
                .id(appointment.getDoctor().getId())
                .firstName(appointment.getDoctor().getFirstName())
                .lastName(appointment.getDoctor().getLastName())
                .specialization(appointment.getDoctor().getSpecialization())
                .email(appointment.getDoctor().getEmail())
                .phoneNumber(appointment.getDoctor().getPhoneNumber())
                .build();

        // Build time slot info DTO
        AppointmentDto.TimeSlotInfo slotInfo = AppointmentDto.TimeSlotInfo.builder()
                .id(appointment.getTimeSlot().getId())
                .date(appointment.getTimeSlot().getSlotDate())
                .startTime(appointment.getTimeSlot().getStartTime())
                .endTime(appointment.getTimeSlot().getEndTime())
                .build();

        // Build main appointment DTO
        return AppointmentDto.builder()
                .id(appointment.getId())
                .patient(patientInfo)
                .doctor(doctorInfo)
                .timeSlot(slotInfo)
                .status(appointment.getStatus())
                .reasonForVisit(appointment.getReasonForVisit())
                .doctorNotes(appointment.getDoctorNotes())
                .prescription(appointment.getPrescription())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
