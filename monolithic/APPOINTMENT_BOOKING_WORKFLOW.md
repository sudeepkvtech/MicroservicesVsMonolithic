# Appointment Booking Workflow - Monolithic Architecture

## Complete End-to-End Flow Documentation

This document provides a **minute-by-minute, line-by-line** trace of the complete appointment booking workflow in the monolithic architecture, showing exactly which code is called, what parameters are passed, and what values are returned at each step.

---

## Table of Contents
1. [Workflow Overview](#workflow-overview)
2. [Entry Point: API Layer](#entry-point-api-layer)
3. [Service Layer Execution](#service-layer-execution)
4. [Repository Layer Operations](#repository-layer-operations)
5. [Entity Business Logic](#entity-business-logic)
6. [Complete Code Trace](#complete-code-trace)
7. [Data Flow Diagram](#data-flow-diagram)
8. [Return Value Chain](#return-value-chain)

---

## Workflow Overview

### High-Level Flow
```
Client Request → Service Layer → Repository Layer → Database
                     ↓              ↓                  ↓
                  Validation    Entity Fetch      SQL Queries
                     ↓              ↓                  ↓
                Business Logic  Data Access      Transactions
                     ↓              ↓                  ↓
                DTO Mapping    Entity Save       Commit/Rollback
                     ↓
               Response DTO
```

### Components Involved
1. **DTO Layer**: `AppointmentDto.BookRequest`, `AppointmentDto`
2. **Service Layer**: `AppointmentService`
3. **Repository Layer**: `UserRepository`, `TimeSlotRepository`, `AppointmentRepository`
4. **Entity Layer**: `User`, `TimeSlot`, `Appointment`
5. **Database Layer**: PostgreSQL with JPA/Hibernate

---

## Entry Point: API Layer

### Note on Controllers
Currently, REST controllers are **not yet implemented** in this codebase (see README.md line 355: "REST controllers - To Be Implemented").

However, when implemented, the typical flow would be:

```java
// Future Implementation: AppointmentController.java
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @PostMapping
    public ResponseEntity<AppointmentDto> bookAppointment(
        @AuthenticationPrincipal User currentUser,
        @RequestBody AppointmentDto.BookRequest request
    ) {
        // This would be the entry point
        // Call: appointmentService.bookAppointment(currentUser.getId(), request)
    }
}
```

**For this documentation, we'll trace from the Service Layer entry point.**

---

## Service Layer Execution

### Entry Point: `AppointmentService.bookAppointment()`

**Location**: `monolithic/src/main/java/com/appointment/service/AppointmentService.java:72`

**Method Signature**:
```java
public AppointmentDto bookAppointment(UUID patientId, AppointmentDto.BookRequest request)
```

**Input Parameters**:
- `patientId`: UUID of the patient (e.g., `550e8400-e29b-41d4-a716-446655440000`)
- `request`: `AppointmentDto.BookRequest` containing:
  - `doctorId`: UUID (e.g., `660e8400-e29b-41d4-a716-446655440001`)
  - `timeSlotId`: UUID (e.g., `770e8400-e29b-41d4-a716-446655440002`)
  - `reasonForVisit`: String (e.g., "Annual checkup")

**Annotations**:
- `@Transactional`: All operations run within a database transaction

---

## Complete Code Trace

### STEP 1: Method Entry and Logging

**File**: `AppointmentService.java`
**Line**: 72-75

```java
public AppointmentDto bookAppointment(UUID patientId, AppointmentDto.BookRequest request) {
    log.info("Booking appointment for patient: {}, slot: {}",
            patientId, request.getTimeSlotId());
```

**What Happens**:
- Method is invoked with `patientId` and `request` parameters
- Logging statement executes via SLF4J logger
- Log output: `"Booking appointment for patient: 550e8400-e29b-41d4-a716-446655440000, slot: 770e8400-e29b-41d4-a716-446655440002"`
- Spring's `@Transactional` starts a database transaction

**Returns**: Nothing (void logging)

---

### STEP 2: Validate Patient Exists

**File**: `AppointmentService.java`
**Line**: 79-81

```java
User patient = userRepository.findById(patientId)
        .orElseThrow(() -> new IllegalArgumentException(
                "Patient not found with ID: " + patientId));
```

**What Happens**:

#### 2.1: Repository Call
- **Method Called**: `UserRepository.findById(UUID id)`
- **Interface**: `JpaRepository<User, UUID>` (inherited method)
- **Location**: `repository/UserRepository.java`
- **Parameter**: `patientId` = `550e8400-e29b-41d4-a716-446655440000`

#### 2.2: Database Query Execution
Spring Data JPA generates and executes:
```sql
SELECT
    u.id, u.first_name, u.last_name, u.email,
    u.password_hash, u.phone_number, u.date_of_birth,
    u.address, u.created_at, u.updated_at
FROM users u
WHERE u.id = '550e8400-e29b-41d4-a716-446655440000'
```

#### 2.3: Result Mapping
Hibernate/JPA maps the result row to a `User` entity:
- Creates new `User` object
- Sets all fields from database columns
- Returns `Optional<User>` containing the user

#### 2.4: Optional Processing
- **If Found**: `Optional.get()` returns the `User` object
- **If Not Found**: `orElseThrow()` throws `IllegalArgumentException` with message: `"Patient not found with ID: 550e8400-e29b-41d4-a716-446655440000"`

**Returns**:
```java
User {
    id: 550e8400-e29b-41d4-a716-446655440000,
    firstName: "John",
    lastName: "Doe",
    email: "john.doe@example.com",
    passwordHash: "$2a$10$...",
    phoneNumber: "+1234567890",
    dateOfBirth: 1990-05-15,
    address: "123 Main St, City, State",
    roles: [ROLE_PATIENT],
    appointments: <lazy-loaded>,
    createdAt: 2024-01-15T10:30:00,
    updatedAt: 2024-01-15T10:30:00
}
```

---

### STEP 3: Lock and Retrieve Time Slot

**File**: `AppointmentService.java`
**Line**: 86-88

```java
TimeSlot timeSlot = timeSlotRepository.findByIdWithLock(request.getTimeSlotId())
        .orElseThrow(() -> new IllegalArgumentException(
                "Time slot not found with ID: " + request.getTimeSlotId()));
```

**What Happens**:

#### 3.1: Repository Call with Pessimistic Lock
- **Method Called**: `TimeSlotRepository.findByIdWithLock(UUID id)`
- **Location**: `repository/TimeSlotRepository.java:66-69`
- **Parameter**: `request.getTimeSlotId()` = `770e8400-e29b-41d4-a716-446655440002`
- **Lock Type**: `@Lock(LockModeType.PESSIMISTIC_WRITE)`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ts FROM TimeSlot ts WHERE ts.id = :id")
Optional<TimeSlot> findByIdWithLock(@Param("id") UUID id);
```

#### 3.2: Database Query with Lock
Spring Data JPA generates:
```sql
SELECT
    ts.id, ts.doctor_id, ts.slot_date, ts.start_time,
    ts.end_time, ts.is_available, ts.max_bookings,
    ts.current_bookings, ts.version, ts.created_at, ts.updated_at
FROM time_slots ts
WHERE ts.id = '770e8400-e29b-41d4-a716-446655440002'
FOR UPDATE  -- Pessimistic lock acquired here
```

**Critical**: The `FOR UPDATE` clause locks this row. Other transactions attempting to read this row with `FOR UPDATE` will **wait** until this transaction commits or rolls back.

#### 3.3: Lock Behavior
- **Current Transaction**: Acquires exclusive write lock
- **Other Transactions**:
  - Can read the row normally (without lock)
  - **Cannot** acquire write lock (will wait)
  - Prevents double-booking race condition

#### 3.4: Result Mapping
Hibernate maps to `TimeSlot` entity:

**Returns**:
```java
TimeSlot {
    id: 770e8400-e29b-41d4-a716-446655440002,
    doctor: <lazy-proxy to Doctor entity>,
    slotDate: 2024-02-20,
    startTime: 10:00:00,
    endTime: 10:30:00,
    isAvailable: true,
    maxBookings: 1,
    currentBookings: 0,
    version: 1,  // Optimistic lock version
    createdAt: 2024-01-10T08:00:00,
    updatedAt: 2024-01-10T08:00:00
}
```

**Note**: The `doctor` field is a lazy-loaded proxy. The actual `Doctor` entity is loaded when accessed (e.g., `timeSlot.getDoctor()`).

---

### STEP 4: Validate Appointment Booking

**File**: `AppointmentService.java`
**Line**: 91

```java
validateAppointmentBooking(patient, timeSlot);
```

**What Happens**: Calls private validation method

#### 4.1: Validation Method Entry
**Location**: `AppointmentService.java:140`

```java
private void validateAppointmentBooking(User patient, TimeSlot timeSlot) {
```

**Parameters**:
- `patient`: The `User` object from Step 2
- `timeSlot`: The `TimeSlot` object from Step 3

---

#### 4.2: Validation Rule 1 - Check if Slot is in Past

**Location**: `AppointmentService.java:142-144`

```java
if (timeSlot.isPast()) {
    throw new IllegalStateException("Cannot book appointment in the past");
}
```

**Sub-Call**: `TimeSlot.isPast()`

**Location**: `model/TimeSlot.java:206`

```java
public boolean isPast() {
    LocalDate today = LocalDate.now();  // Get current date
    LocalTime now = LocalTime.now();    // Get current time

    if (slotDate.isBefore(today)) {
        return true;  // Slot date is before today
    }

    if (slotDate.isEqual(today) && startTime.isBefore(now)) {
        return true;  // Slot is today but time has passed
    }

    return false;  // Slot is in the future
}
```

**Execution Example**:
- `today` = `2024-02-15` (current date)
- `now` = `14:30:00` (current time)
- `timeSlot.slotDate` = `2024-02-20` (future date)
- `slotDate.isBefore(today)` = `false` (2024-02-20 is not before 2024-02-15)
- Returns: `false`

**Result**: No exception thrown, validation passes

---

#### 4.3: Validation Rule 2 - Check if Slot is Available

**Location**: `AppointmentService.java:147-149`

```java
if (!timeSlot.getIsAvailable()) {
    throw new IllegalStateException("Time slot is not available");
}
```

**Getter Call**: `timeSlot.getIsAvailable()`
- Lombok-generated getter returns: `true`
- Negation: `!true` = `false`
- If condition: `false` (not entered)

**Result**: No exception thrown, validation passes

---

#### 4.4: Validation Rule 3 - Check for Duplicate Booking

**Location**: `AppointmentService.java:153-158`

```java
boolean hasExistingAppointment = appointmentRepository
        .existsActiveAppointmentForPatientWithDoctorOnDate(
                patient.getId(),
                timeSlot.getDoctor().getId(),
                timeSlot.getSlotDate()
        );
```

**Sub-Call 4.4.1**: `timeSlot.getDoctor()`
- Returns lazy-loaded `Doctor` proxy
- Triggers database query to load doctor:
```sql
SELECT
    d.id, d.first_name, d.last_name, d.email,
    d.phone_number, d.specialization, d.qualifications,
    d.experience_years, d.consultation_fee, d.hospital_id,
    d.bio, d.is_accepting_patients, d.created_at, d.updated_at
FROM doctors d
WHERE d.id = '660e8400-e29b-41d4-a716-446655440001'
```

**Returns**:
```java
Doctor {
    id: 660e8400-e29b-41d4-a716-446655440001,
    firstName: "Jane",
    lastName: "Smith",
    email: "dr.jane.smith@hospital.com",
    phoneNumber: "+1987654321",
    specialization: "Cardiology",
    qualifications: "MBBS, MD",
    experienceYears: 10,
    consultationFee: 150.00,
    hospitalId: "HOSP001",
    bio: "Experienced cardiologist...",
    isAcceptingPatients: true,
    timeSlots: <lazy-loaded>,
    appointments: <lazy-loaded>,
    createdAt: 2024-01-01T09:00:00,
    updatedAt: 2024-01-01T09:00:00
}
```

**Sub-Call 4.4.2**: `appointmentRepository.existsActiveAppointmentForPatientWithDoctorOnDate()`

**Location**: `repository/AppointmentRepository.java:268-278`

```java
@Query("SELECT COUNT(a) > 0 FROM Appointment a " +
        "JOIN a.timeSlot ts " +
        "WHERE a.patient.id = :patientId " +
        "AND a.doctor.id = :doctorId " +
        "AND ts.slotDate = :date " +
        "AND a.status IN ('PENDING', 'CONFIRMED')")
boolean existsActiveAppointmentForPatientWithDoctorOnDate(
        @Param("patientId") UUID patientId,
        @Param("doctorId") UUID doctorId,
        @Param("date") LocalDate date
);
```

**Parameters**:
- `patientId`: `550e8400-e29b-41d4-a716-446655440000`
- `doctorId`: `660e8400-e29b-41d4-a716-446655440001`
- `date`: `2024-02-20`

**Database Query**:
```sql
SELECT COUNT(a.id) > 0
FROM appointments a
INNER JOIN time_slots ts ON a.slot_id = ts.id
WHERE a.patient_id = '550e8400-e29b-41d4-a716-446655440000'
  AND a.doctor_id = '660e8400-e29b-41d4-a716-446655440001'
  AND ts.slot_date = '2024-02-20'
  AND a.status IN ('PENDING', 'CONFIRMED')
```

**Query Result**:
- If count > 0: Returns `true` (duplicate exists)
- If count = 0: Returns `false` (no duplicate)

**Example Result**: `false` (no existing appointment)

**Back to Validation**:
```java
if (hasExistingAppointment) {
    throw new IllegalStateException(
            "You already have an appointment with this doctor on this date");
}
```

- `hasExistingAppointment` = `false`
- If condition: `false` (not entered)

**Result**: No exception thrown, all validations pass

#### 4.5: Return from Validation
**Returns**: `void` (no exceptions thrown means validation successful)

---

### STEP 5: Book the Time Slot

**File**: `AppointmentService.java`
**Line**: 95-101

```java
try {
    timeSlot.book();
} catch (IllegalStateException e) {
    log.error("Failed to book slot {}: {}", timeSlot.getId(), e.getMessage());
    throw new IllegalStateException("Time slot is no longer available", e);
}
```

#### 5.1: Call Entity Business Method
**Method Called**: `TimeSlot.book()`
**Location**: `model/TimeSlot.java:138`

```java
public void book() {
    if (!isAvailable) {
        throw new IllegalStateException("Time slot is not available for booking");
    }

    if (currentBookings >= maxBookings) {
        throw new IllegalStateException("Time slot is fully booked");
    }

    currentBookings++;

    if (currentBookings >= maxBookings) {
        isAvailable = false;
    }
}
```

**Execution Trace**:

**Line 140**: Check availability
```java
if (!isAvailable)
```
- `isAvailable` = `true`
- `!true` = `false`
- Condition not entered

**Line 146**: Check capacity
```java
if (currentBookings >= maxBookings)
```
- `currentBookings` = `0`
- `maxBookings` = `1`
- `0 >= 1` = `false`
- Condition not entered

**Line 152**: Increment bookings
```java
currentBookings++;
```
- Before: `currentBookings` = `0`
- After: `currentBookings` = `1`

**Line 155**: Check if fully booked
```java
if (currentBookings >= maxBookings)
```
- `currentBookings` = `1`
- `maxBookings` = `1`
- `1 >= 1` = `true`
- Condition entered

**Line 156**: Mark unavailable
```java
isAvailable = false;
```
- Before: `isAvailable` = `true`
- After: `isAvailable` = `false`

**State After Execution**:
```java
TimeSlot {
    id: 770e8400-e29b-41d4-a716-446655440002,
    // ... other fields unchanged ...
    isAvailable: false,  // CHANGED
    maxBookings: 1,
    currentBookings: 1,  // CHANGED
    version: 1
}
```

**Note**: Changes are in-memory only. Not yet persisted to database.

**Returns**: `void` (state modified in place)

---

### STEP 6: Create Appointment Entity

**File**: `AppointmentService.java`
**Line**: 104-110

```java
Appointment appointment = Appointment.builder()
        .patient(patient)
        .doctor(timeSlot.getDoctor())
        .timeSlot(timeSlot)
        .status(Appointment.AppointmentStatus.PENDING)
        .reasonForVisit(request.getReasonForVisit())
        .build();
```

**What Happens**:

#### 6.1: Builder Pattern Execution
Lombok's `@Builder` generates a builder class. Step-by-step:

**Line 104**: `Appointment.builder()`
- Creates new `AppointmentBuilder` instance
- Returns: `AppointmentBuilder {}`

**Line 105**: `.patient(patient)`
- Sets patient field in builder
- Returns: `AppointmentBuilder { patient: <User object from Step 2> }`

**Line 106**: `.doctor(timeSlot.getDoctor())`
- Calls `timeSlot.getDoctor()` → Returns `Doctor` object (already loaded in Step 4.4.1)
- Sets doctor field in builder
- Returns: `AppointmentBuilder { patient: ..., doctor: <Doctor object> }`

**Line 107**: `.timeSlot(timeSlot)`
- Sets timeSlot field in builder
- Returns: `AppointmentBuilder { patient: ..., doctor: ..., timeSlot: <TimeSlot object> }`

**Line 108**: `.status(Appointment.AppointmentStatus.PENDING)`
- Sets status to enum value `PENDING`
- Returns: `AppointmentBuilder { ..., status: PENDING }`

**Line 109**: `.reasonForVisit(request.getReasonForVisit())`
- Gets reason from request: `"Annual checkup"`
- Sets reasonForVisit field
- Returns: `AppointmentBuilder { ..., reasonForVisit: "Annual checkup" }`

**Line 110**: `.build()`
- Creates new `Appointment` instance with all builder fields
- Invokes `AllArgsConstructor` to set fields

**Entity Created**:
```java
Appointment {
    id: null,  // Not yet persisted, no ID assigned
    patient: User { id: 550e8400-e29b-41d4-a716-446655440000, ... },
    doctor: Doctor { id: 660e8400-e29b-41d4-a716-446655440001, ... },
    timeSlot: TimeSlot { id: 770e8400-e29b-41d4-a716-446655440002, ... },
    status: PENDING,
    reasonForVisit: "Annual checkup",
    doctorNotes: null,
    prescription: null,
    payment: null,
    createdAt: null,  // Will be set by @PrePersist
    updatedAt: null   // Will be set by @PrePersist
}
```

**Returns**: The newly created `Appointment` object

---

### STEP 7: Save Appointment to Database

**File**: `AppointmentService.java`
**Line**: 113

```java
Appointment savedAppointment = appointmentRepository.save(appointment);
```

**What Happens**:

#### 7.1: Repository Save Call
**Method Called**: `AppointmentRepository.save(Appointment appointment)`
**Interface**: `JpaRepository<Appointment, UUID>` (inherited)
**Parameter**: The `Appointment` object from Step 6

#### 7.2: JPA Pre-Persist Lifecycle

Before SQL execution, JPA triggers lifecycle callbacks:

**Callback 1**: `BaseEntity.onCreate()` (inherited by Appointment)
**Location**: `model/BaseEntity.java:67-75`

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) {
        createdAt = LocalDateTime.now();  // Sets current timestamp
    }
    if (updatedAt == null) {
        updatedAt = LocalDateTime.now();  // Sets current timestamp
    }
}
```

**Execution**:
- `createdAt` is `null` → sets to `2024-02-15T14:35:22` (current time)
- `updatedAt` is `null` → sets to `2024-02-15T14:35:22` (current time)

**State After PrePersist**:
```java
Appointment {
    id: null,  // Still null, will be generated by database
    patient: User { ... },
    doctor: Doctor { ... },
    timeSlot: TimeSlot { ... },
    status: PENDING,
    reasonForVisit: "Annual checkup",
    doctorNotes: null,
    prescription: null,
    payment: null,
    createdAt: 2024-02-15T14:35:22,  // CHANGED
    updatedAt: 2024-02-15T14:35:22   // CHANGED
}
```

#### 7.3: ID Generation
Hibernate generates UUID for the entity:
- Strategy: `GenerationType.UUID`
- Generated ID: `880e8400-e29b-41d4-a716-446655440003` (example)

#### 7.4: SQL INSERT Execution

**Generated SQL**:
```sql
INSERT INTO appointments (
    id, patient_id, doctor_id, slot_id, status,
    reason_for_visit, doctor_notes, prescription,
    created_at, updated_at
) VALUES (
    '880e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440000',
    '660e8400-e29b-41d4-a716-446655440001',
    '770e8400-e29b-41d4-a716-446655440002',
    'PENDING',
    'Annual checkup',
    NULL,
    NULL,
    '2024-02-15 14:35:22',
    '2024-02-15 14:35:22'
)
```

**Database Execution**:
1. Insert row into `appointments` table
2. Foreign key constraints validated
3. Indexes updated
4. Row committed to write-ahead log (not yet committed to disk)

#### 7.5: Update Time Slot (Cascading)

The `timeSlot` object was modified in Step 5 (marked as unavailable). JPA detects this dirty entity and updates it:

**Generated SQL**:
```sql
UPDATE time_slots
SET is_available = false,
    current_bookings = 1,
    version = 2,  -- Optimistic lock version incremented
    updated_at = '2024-02-15 14:35:22'
WHERE id = '770e8400-e29b-41d4-a716-446655440002'
  AND version = 1  -- Optimistic lock check
```

**Optimistic Lock Check**:
- If another transaction modified this row and incremented `version`, this UPDATE will affect 0 rows
- JPA will throw `OptimisticLockException`
- In our case, no conflict → UPDATE succeeds

**Database Execution**:
1. Update row in `time_slots` table
2. Set `is_available = false`, `current_bookings = 1`, `version = 2`
3. Update timestamp

#### 7.6: Transaction State

At this point:
- INSERT and UPDATE statements are executed
- Changes are in the transaction buffer
- **NOT YET COMMITTED** to database
- If exception occurs, entire transaction will rollback

#### 7.7: Return from Save

**Returns**:
```java
Appointment {
    id: 880e8400-e29b-41d4-a716-446655440003,  // NOW HAS ID
    patient: User { id: 550e8400-e29b-41d4-a716-446655440000, ... },
    doctor: Doctor { id: 660e8400-e29b-41d4-a716-446655440001, ... },
    timeSlot: TimeSlot {
        id: 770e8400-e29b-41d4-a716-446655440002,
        isAvailable: false,      // Updated
        currentBookings: 1,      // Updated
        version: 2,              // Updated
        ...
    },
    status: PENDING,
    reasonForVisit: "Annual checkup",
    doctorNotes: null,
    prescription: null,
    payment: null,
    createdAt: 2024-02-15T14:35:22,
    updatedAt: 2024-02-15T14:35:22
}
```

---

### STEP 8: Logging Success

**File**: `AppointmentService.java`
**Line**: 116

```java
log.info("Successfully booked appointment {}", savedAppointment.getId());
```

**Execution**:
- Calls `savedAppointment.getId()` → Returns `880e8400-e29b-41d4-a716-446655440003`
- Logs: `"Successfully booked appointment 880e8400-e29b-41d4-a716-446655440003"`

**Returns**: `void`

---

### STEP 9: Convert Entity to DTO

**File**: `AppointmentService.java`
**Line**: 125

```java
return convertToDto(savedAppointment);
```

#### 9.1: DTO Conversion Method
**Method Called**: `AppointmentService.convertToDto(Appointment appointment)`
**Location**: `AppointmentService.java:306`

```java
private AppointmentDto convertToDto(Appointment appointment) {
```

**Parameter**: The saved `Appointment` entity from Step 7

---

#### 9.2: Build Patient Info DTO

**Location**: `AppointmentService.java:308-314`

```java
AppointmentDto.PatientInfo patientInfo = AppointmentDto.PatientInfo.builder()
        .id(appointment.getPatient().getId())
        .firstName(appointment.getPatient().getFirstName())
        .lastName(appointment.getPatient().getLastName())
        .email(appointment.getPatient().getEmail())
        .phoneNumber(appointment.getPatient().getPhoneNumber())
        .build();
```

**Execution**:
- `appointment.getPatient()` → Returns `User` object (already loaded, no query)
- Chain of getter calls extracts data
- Builder creates nested DTO

**Created Object**:
```java
AppointmentDto.PatientInfo {
    id: 550e8400-e29b-41d4-a716-446655440000,
    firstName: "John",
    lastName: "Doe",
    email: "john.doe@example.com",
    phoneNumber: "+1234567890"
}
```

---

#### 9.3: Build Doctor Info DTO

**Location**: `AppointmentService.java:317-324`

```java
AppointmentDto.DoctorInfo doctorInfo = AppointmentDto.DoctorInfo.builder()
        .id(appointment.getDoctor().getId())
        .firstName(appointment.getDoctor().getFirstName())
        .lastName(appointment.getDoctor().getLastName())
        .specialization(appointment.getDoctor().getSpecialization())
        .email(appointment.getDoctor().getEmail())
        .phoneNumber(appointment.getDoctor().getPhoneNumber())
        .build();
```

**Execution**:
- `appointment.getDoctor()` → Returns `Doctor` object (already loaded)
- Extracts doctor data
- Builder creates nested DTO

**Created Object**:
```java
AppointmentDto.DoctorInfo {
    id: 660e8400-e29b-41d4-a716-446655440001,
    firstName: "Jane",
    lastName: "Smith",
    specialization: "Cardiology",
    email: "dr.jane.smith@hospital.com",
    phoneNumber: "+1987654321"
}
```

---

#### 9.4: Build Time Slot Info DTO

**Location**: `AppointmentService.java:327-332`

```java
AppointmentDto.TimeSlotInfo slotInfo = AppointmentDto.TimeSlotInfo.builder()
        .id(appointment.getTimeSlot().getId())
        .date(appointment.getTimeSlot().getSlotDate())
        .startTime(appointment.getTimeSlot().getStartTime())
        .endTime(appointment.getTimeSlot().getEndTime())
        .build();
```

**Execution**:
- `appointment.getTimeSlot()` → Returns `TimeSlot` object
- Extracts slot data
- Builder creates nested DTO

**Created Object**:
```java
AppointmentDto.TimeSlotInfo {
    id: 770e8400-e29b-41d4-a716-446655440002,
    date: 2024-02-20,
    startTime: 10:00:00,
    endTime: 10:30:00
}
```

---

#### 9.5: Build Main Appointment DTO

**Location**: `AppointmentService.java:335-346`

```java
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
```

**Execution**:
- Builder pattern assembles final DTO
- Uses nested DTOs created in previous steps
- Copies status, notes, timestamps

**Final DTO Object**:
```java
AppointmentDto {
    id: 880e8400-e29b-41d4-a716-446655440003,
    patient: PatientInfo {
        id: 550e8400-e29b-41d4-a716-446655440000,
        firstName: "John",
        lastName: "Doe",
        email: "john.doe@example.com",
        phoneNumber: "+1234567890"
    },
    doctor: DoctorInfo {
        id: 660e8400-e29b-41d4-a716-446655440001,
        firstName: "Jane",
        lastName: "Smith",
        specialization: "Cardiology",
        email: "dr.jane.smith@hospital.com",
        phoneNumber: "+1987654321"
    },
    timeSlot: TimeSlotInfo {
        id: 770e8400-e29b-41d4-a716-446655440002,
        date: 2024-02-20,
        startTime: 10:00:00,
        endTime: 10:30:00
    },
    status: PENDING,
    reasonForVisit: "Annual checkup",
    doctorNotes: null,
    prescription: null,
    payment: null,
    createdAt: 2024-02-15T14:35:22,
    updatedAt: 2024-02-15T14:35:22
}
```

**Returns**: The complete `AppointmentDto` object

---

### STEP 10: Transaction Commit

**Triggered By**: `@Transactional` annotation on `bookAppointment()` method

**What Happens**:

1. **Method Returns Successfully**
   - No exceptions thrown
   - Spring detects successful method completion

2. **Transaction Commit Phase**
   ```
   Database Operations:
   - Flush all pending changes to database
   - Execute COMMIT statement
   - Release pessimistic lock on time_slots row
   - Make all changes permanent and visible to other transactions
   ```

3. **Database State After Commit**:

   **appointments table** - New row:
   ```
   id: 880e8400-e29b-41d4-a716-446655440003
   patient_id: 550e8400-e29b-41d4-a716-446655440000
   doctor_id: 660e8400-e29b-41d4-a716-446655440001
   slot_id: 770e8400-e29b-41d4-a716-446655440002
   status: PENDING
   reason_for_visit: Annual checkup
   doctor_notes: NULL
   prescription: NULL
   created_at: 2024-02-15 14:35:22
   updated_at: 2024-02-15 14:35:22
   ```

   **time_slots table** - Updated row:
   ```
   id: 770e8400-e29b-41d4-a716-446655440002
   ...
   is_available: false  (was true)
   current_bookings: 1  (was 0)
   version: 2           (was 1)
   updated_at: 2024-02-15 14:35:22
   ```

4. **Lock Release**
   - Pessimistic write lock released
   - Other transactions can now access the row

---

### STEP 11: Return Response

**Returns to Caller**: The `AppointmentDto` object from Step 9

**When Controller is Implemented**:
```java
// Future: AppointmentController receives this DTO
AppointmentDto responseDto = appointmentService.bookAppointment(...);

// Spring converts DTO to JSON
// HTTP Response Status: 201 Created
// Response Body (JSON):
{
    "id": "880e8400-e29b-41d4-a716-446655440003",
    "patient": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "firstName": "John",
        "lastName": "Doe",
        "email": "john.doe@example.com",
        "phoneNumber": "+1234567890"
    },
    "doctor": {
        "id": "660e8400-e29b-41d4-a716-446655440001",
        "firstName": "Jane",
        "lastName": "Smith",
        "specialization": "Cardiology",
        "email": "dr.jane.smith@hospital.com",
        "phoneNumber": "+1987654321"
    },
    "timeSlot": {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "date": "2024-02-20",
        "startTime": "10:00:00",
        "endTime": "10:30:00"
    },
    "status": "PENDING",
    "reasonForVisit": "Annual checkup",
    "doctorNotes": null,
    "prescription": null,
    "payment": null,
    "createdAt": "2024-02-15T14:35:22",
    "updatedAt": "2024-02-15T14:35:22"
}
```

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      BOOKING WORKFLOW                           │
└─────────────────────────────────────────────────────────────────┘

INPUT:
┌──────────────────────────────────────┐
│ patientId: UUID                      │
│ request: {                           │
│   doctorId: UUID                     │
│   timeSlotId: UUID                   │
│   reasonForVisit: String             │
│ }                                    │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 1: Start Transaction           │
│ @Transactional begins                │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 2: Fetch Patient                │
│ userRepository.findById(patientId)   │
│ → SELECT FROM users                  │
│ → Returns: User entity               │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 3: Lock & Fetch TimeSlot        │
│ timeSlotRepository                   │
│   .findByIdWithLock(slotId)          │
│ → SELECT ... FOR UPDATE              │
│ → Acquires pessimistic lock          │
│ → Returns: TimeSlot entity           │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 4: Validate Booking             │
│ validateAppointmentBooking()         │
│   → timeSlot.isPast()                │
│   → timeSlot.getIsAvailable()        │
│   → Fetch doctor (lazy load)         │
│   → appointmentRepository            │
│       .existsActive...()             │
│   → SELECT COUNT ... (duplicate)     │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 5: Book TimeSlot                │
│ timeSlot.book()                      │
│   → currentBookings++                │
│   → isAvailable = false              │
│ (In-memory change, not yet saved)    │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 6: Create Appointment           │
│ Appointment.builder()                │
│   .patient(patient)                  │
│   .doctor(doctor)                    │
│   .timeSlot(timeSlot)                │
│   .status(PENDING)                   │
│   .reasonForVisit(...)               │
│   .build()                           │
│ → Returns: New Appointment entity    │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 7: Save to Database             │
│ appointmentRepository.save()         │
│   → @PrePersist: Set timestamps      │
│   → Generate UUID                    │
│   → INSERT INTO appointments         │
│   → UPDATE time_slots (cascade)      │
│ → Returns: Saved Appointment         │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 8: Convert to DTO               │
│ convertToDto(appointment)            │
│   → Build PatientInfo DTO            │
│   → Build DoctorInfo DTO             │
│   → Build TimeSlotInfo DTO           │
│   → Build AppointmentDto             │
│ → Returns: AppointmentDto            │
└──────────────────────────────────────┘
           ↓
           ↓
┌──────────────────────────────────────┐
│ STEP 9: Commit Transaction           │
│ @Transactional commits               │
│   → COMMIT                           │
│   → Release lock                     │
│   → Changes permanent                │
└──────────────────────────────────────┘
           ↓
           ↓
OUTPUT:
┌──────────────────────────────────────┐
│ AppointmentDto {                     │
│   id: UUID (generated)               │
│   patient: PatientInfo               │
│   doctor: DoctorInfo                 │
│   timeSlot: TimeSlotInfo             │
│   status: PENDING                    │
│   reasonForVisit: String             │
│   createdAt: LocalDateTime           │
│   updatedAt: LocalDateTime           │
│ }                                    │
└──────────────────────────────────────┘
```

---

## Return Value Chain

### Detailed Return Value Trace

```java
// Entry
AppointmentService.bookAppointment(patientId, request)

  // Step 2
  → userRepository.findById(patientId)
    → Optional<User>
    → User entity (extracted from Optional)

  // Step 3
  → timeSlotRepository.findByIdWithLock(slotId)
    → Optional<TimeSlot>
    → TimeSlot entity (extracted from Optional)

  // Step 4
  → validateAppointmentBooking(patient, timeSlot)
    → timeSlot.isPast()
      → boolean: false
    → timeSlot.getIsAvailable()
      → Boolean: true
    → timeSlot.getDoctor()
      → Doctor entity (lazy-loaded)
    → appointmentRepository.existsActive...()
      → boolean: false
    → void (returns nothing, throws on error)

  // Step 5
  → timeSlot.book()
    → void (modifies state in-place)

  // Step 6
  → Appointment.builder()...build()
    → Appointment entity (new, unsaved)

  // Step 7
  → appointmentRepository.save(appointment)
    → Appointment entity (saved, with ID)

  // Step 9
  → convertToDto(savedAppointment)
    → AppointmentDto.PatientInfo.builder()...build()
      → PatientInfo DTO
    → AppointmentDto.DoctorInfo.builder()...build()
      → DoctorInfo DTO
    → AppointmentDto.TimeSlotInfo.builder()...build()
      → TimeSlotInfo DTO
    → AppointmentDto.builder()...build()
      → AppointmentDto (final result)

// Return
← AppointmentDto
```

---

## Database State Changes

### Before Booking

**time_slots table**:
```
id: 770e8400-e29b-41d4-a716-446655440002
is_available: true
current_bookings: 0
version: 1
```

**appointments table**:
```
(No row exists yet)
```

### After Booking

**time_slots table**:
```
id: 770e8400-e29b-41d4-a716-446655440002
is_available: false        ← CHANGED
current_bookings: 1        ← CHANGED
version: 2                 ← CHANGED
updated_at: 2024-02-15... ← CHANGED
```

**appointments table**:
```
id: 880e8400-e29b-41d4-a716-446655440003  ← NEW ROW
patient_id: 550e8400...
doctor_id: 660e8400...
slot_id: 770e8400...
status: PENDING
reason_for_visit: Annual checkup
created_at: 2024-02-15...
updated_at: 2024-02-15...
```

---

## Concurrency Handling

### Race Condition Prevention

**Scenario**: Two users (User A and User B) try to book the same slot simultaneously.

#### Timeline:

```
Time    User A Transaction              User B Transaction
────────────────────────────────────────────────────────────
T1      BEGIN TRANSACTION
T2      SELECT ... FOR UPDATE           BEGIN TRANSACTION
        (Acquires lock)
T3      Slot validation passes          SELECT ... FOR UPDATE
T4      timeSlot.book()                 (Waits for lock...)
T5      INSERT appointment              (Still waiting...)
T6      UPDATE time_slots               (Still waiting...)
T7      COMMIT                          (Lock released, query executes)
        (Lock released)
T8                                      Slot validation: isAvailable=false
T9                                      timeSlot.book() throws exception
T10                                     ROLLBACK
```

**Result**:
- User A successfully books the appointment
- User B receives error: "Time slot is not available for booking"
- **No double-booking occurs**

---

## Error Scenarios

### Scenario 1: Patient Not Found

**Input**: Invalid `patientId`

**Execution Path**:
- Step 2: `userRepository.findById()` returns `Optional.empty()`
- `orElseThrow()` executes
- **Exception Thrown**: `IllegalArgumentException("Patient not found with ID: ...")`
- Transaction rolls back
- No database changes
- Error response returned to client

---

### Scenario 2: Time Slot Not Found

**Input**: Invalid `timeSlotId`

**Execution Path**:
- Step 3: `timeSlotRepository.findByIdWithLock()` returns `Optional.empty()`
- **Exception Thrown**: `IllegalArgumentException("Time slot not found with ID: ...")`
- Transaction rolls back
- Lock never acquired
- No database changes

---

### Scenario 3: Time Slot Already Booked

**Input**: Valid IDs, but slot is already booked

**Execution Path**:
- Step 2-3: Fetch patient and slot successfully
- Step 4: Validation passes
- Step 5: `timeSlot.book()` called
- Line 140 in TimeSlot.java: `if (!isAvailable)` → `true`
- **Exception Thrown**: `IllegalStateException("Time slot is not available for booking")`
- Caught in Step 5
- Re-thrown with message: "Time slot is no longer available"
- Transaction rolls back
- Lock released
- No database changes

---

### Scenario 4: Duplicate Booking Same Day

**Input**: Patient already has appointment with same doctor on same day

**Execution Path**:
- Step 2-3: Fetch patient and slot successfully
- Step 4.4: `existsActiveAppointmentForPatientWithDoctorOnDate()` returns `true`
- Line 160 in AppointmentService.java: `if (hasExistingAppointment)` → `true`
- **Exception Thrown**: `IllegalStateException("You already have an appointment with this doctor on this date")`
- Transaction rolls back
- Lock released
- No database changes

---

### Scenario 5: Booking Past Slot

**Input**: Time slot date/time is in the past

**Execution Path**:
- Step 2-3: Fetch patient and slot successfully
- Step 4.2: `timeSlot.isPast()` returns `true`
- Line 142 in AppointmentService.java: `if (timeSlot.isPast())` → `true`
- **Exception Thrown**: `IllegalStateException("Cannot book appointment in the past")`
- Transaction rolls back
- Lock released
- No database changes

---

## Performance Characteristics

### Query Count: 6 Queries Total

1. **SELECT users** (Step 2)
2. **SELECT time_slots FOR UPDATE** (Step 3)
3. **SELECT doctors** (Step 4.4.1 - Lazy load)
4. **SELECT COUNT appointments** (Step 4.4.2 - Duplicate check)
5. **INSERT appointments** (Step 7)
6. **UPDATE time_slots** (Step 7)

### Optimization Opportunities

1. **Reduce lazy loading**: Fetch doctor eagerly with time slot
   ```java
   @Query("SELECT ts FROM TimeSlot ts " +
          "LEFT JOIN FETCH ts.doctor " +
          "WHERE ts.id = :id")
   ```
   This would eliminate Query #3.

2. **Batch operations**: If booking multiple appointments, use batch inserts

3. **Caching**: Cache doctor information (changes infrequently)

---

## Summary

### Complete Workflow Steps

| Step | Component | Method | Database Queries | Returns |
|------|-----------|--------|------------------|---------|
| 1 | Service | `bookAppointment()` | 0 | - |
| 2 | Repository | `findById(patientId)` | 1 SELECT | `User` |
| 3 | Repository | `findByIdWithLock(slotId)` | 1 SELECT FOR UPDATE | `TimeSlot` |
| 4 | Service | `validateAppointmentBooking()` | 2 SELECT | void |
| 5 | Entity | `timeSlot.book()` | 0 | void |
| 6 | Entity | `Appointment.builder()` | 0 | `Appointment` |
| 7 | Repository | `save(appointment)` | 1 INSERT, 1 UPDATE | `Appointment` |
| 8 | Service | `convertToDto()` | 0 | `AppointmentDto` |
| 9 | Spring | Transaction commit | COMMIT | - |

**Total Database Operations**: 6 queries (4 SELECT, 1 INSERT, 1 UPDATE)

### Key Design Patterns Used

1. **Repository Pattern**: Data access abstraction
2. **Service Layer Pattern**: Business logic encapsulation
3. **DTO Pattern**: API contract separation
4. **Builder Pattern**: Object construction
5. **Pessimistic Locking**: Concurrency control
6. **Template Method**: BaseEntity common structure
7. **Facade Pattern**: Simple interface for complex operation

### SOLID Principles Applied

1. **Single Responsibility**: Each class has one reason to change
2. **Open/Closed**: Extensible without modification
3. **Liskov Substitution**: Entities can be mocked for testing
4. **Interface Segregation**: Repositories have focused interfaces
5. **Dependency Inversion**: Depend on abstractions (repositories)

---

## Conclusion

This document has traced **every single line of code** executed during the appointment booking workflow in the monolithic architecture, showing:

- Exact method calls with line numbers
- Database queries generated
- Return values at each step
- State changes in entities
- Transaction boundaries
- Error handling paths
- Concurrency control mechanisms

The monolithic architecture provides a **straightforward, transactional, and consistent** approach to booking appointments, with strong ACID guarantees and clear code flow from API to database and back.
