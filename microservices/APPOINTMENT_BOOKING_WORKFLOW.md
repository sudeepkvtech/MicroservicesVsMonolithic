# Appointment Booking Workflow - Microservices Architecture

## Complete End-to-End Flow Documentation

This document provides a **minute-by-minute, component-by-component** trace of the complete appointment booking workflow in the microservices architecture, showing exactly which services are called, what events are published, how data flows across service boundaries, and what happens asynchronously vs synchronously.

---

## Table of Contents
1. [Workflow Overview](#workflow-overview)
2. [Architecture Components](#architecture-components)
3. [Synchronous Flow (Steps 1-7)](#synchronous-flow-steps-1-7)
4. [Asynchronous Flow (Steps 8-15)](#asynchronous-flow-steps-8-15)
5. [Complete Code Trace](#complete-code-trace)
6. [Event Flow Diagram](#event-flow-diagram)
7. [Comparison with Monolithic](#comparison-with-monolithic)
8. [Failure Scenarios](#failure-scenarios)

---

## Workflow Overview

### High-Level Architecture

```
┌─────────────┐
│   Client    │
│  (Browser)  │
└──────┬──────┘
       │ HTTP POST /api/appointments
       ↓
┌─────────────────┐
│  API Gateway    │
│   (Port 8080)   │
└──────┬──────────┘
       │ 1. Validate JWT (REST → User Service)
       │ 2. Forward request
       ↓
┌──────────────────────┐
│ Appointment Service  │  ← Main orchestrator
│   (Port 8083)        │
└──────┬───────────────┘
       │ 3. Lock time slot (REST → Doctor Service)
       │ 4. Create appointment in database
       │ 5. Publish AppointmentCreatedEvent → Kafka
       │ 6. Return success response
       ↓
       User receives immediate response here ✓

═══════════════════════════════════════════════════════════
  BELOW THIS LINE: ASYNCHRONOUS (User doesn't wait)
═══════════════════════════════════════════════════════════

       │
       ↓ (Kafka Event Bus)
       │
   ┌───┴────────────────────────────────────┐
   │                                        │
   ↓                                        ↓
┌─────────────────┐                ┌──────────────────┐
│ Payment Service │                │ Notification     │
│  (Port 8084)    │                │ Service          │
└────────┬────────┘                │ (Port 8085)      │
         │                         └──────────────────┘
         │ 7. Create payment              │
         │ 8. Call payment gateway        │ 8. Send confirmation email
         │ 9. Publish PaymentCompleted    │ 9. Log notification
         ↓
┌─────────────────────┐
│ Appointment Service │ ← Receives event
│  (Port 8083)        │
└─────────────────────┘
         │ 10. Update status to CONFIRMED
         ↓
      [DONE]
```

### Key Characteristics

**Synchronous Operations** (User waits):
1. JWT validation (Security critical)
2. Time slot locking (Prevents double-booking)
3. Appointment creation (Core business logic)
4. Response to user (Immediate feedback)

**Asynchronous Operations** (Eventual consistency):
5. Payment processing (Can take time)
6. Email notifications (Non-critical)
7. SMS sending (Non-critical)
8. Analytics updates (Non-critical)

---

## Architecture Components

### Services Involved

| Service | Port | Database | Purpose | Communication |
|---------|------|----------|---------|---------------|
| **API Gateway** | 8080 | None | Route requests, validate JWT | REST only |
| **User Service** | 8081 | user_db | Authentication, user management | REST only |
| **Doctor Service** | 8082 | doctor_db | Doctors, time slots | REST + Kafka consumer |
| **Appointment Service** | 8083 | appointment_db | Core booking logic | REST + Kafka producer/consumer |
| **Payment Service** | 8084 | payment_db | Payment processing | Kafka producer/consumer |
| **Notification Service** | 8085 | notification_db | Email/SMS sending | Kafka consumer |

### Kafka Topics

| Topic Name | Producer | Consumers | Purpose |
|-----------|----------|-----------|---------|
| `appointment-created` | Appointment Service | Payment Service, Notification Service | Trigger payment and send confirmation |
| `payment-completed` | Payment Service | Appointment Service, Notification Service | Confirm appointment, send receipt |
| `appointment-cancelled` | Appointment Service | Payment Service, Notification Service, Doctor Service | Refund, notify, free slot |

### Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Message Broker**: Apache Kafka 3.6.0
- **Databases**: PostgreSQL 15 (one per service)
- **API Gateway**: Spring Cloud Gateway 4.1.0
- **Authentication**: JWT tokens
- **Serialization**: JSON (Jackson)

---

## Synchronous Flow (Steps 1-7)

This is the **blocking** part of the workflow. The user's HTTP request waits for these steps to complete before receiving a response.

---

### STEP 1: Client Sends Request

**Component**: Client (Browser/Mobile App)

**HTTP Request**:
```http
POST http://localhost:8080/api/appointments HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "doctorId": "660e8400-e29b-41d4-a716-446655440001",
  "timeSlotId": "770e8400-e29b-41d4-a716-446655440002",
  "reasonForVisit": "Annual checkup"
}
```

**Request Components**:
- **Method**: POST
- **Endpoint**: `/api/appointments`
- **Headers**:
  - `Authorization`: JWT token (contains user ID, roles, expiry)
  - `Content-Type`: application/json
- **Body**: JSON with booking details

**What Happens**:
- Request sent over TCP/IP to API Gateway
- TLS/SSL encryption (HTTPS)
- Arrives at API Gateway (port 8080)

---

### STEP 2: API Gateway - JWT Validation

**Component**: API Gateway (Spring Cloud Gateway)
**Port**: 8080

#### 2.1: Receive Request

**Gateway Filter Chain**:
```java
// Pseudo-code for API Gateway
@Component
public class JwtAuthenticationFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract JWT from Authorization header
        String authHeader = exchange.getRequest()
            .getHeaders()
            .getFirst("Authorization"); // "Bearer eyJ..."

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Reject: Missing or invalid token
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String jwtToken = authHeader.substring(7); // Remove "Bearer "
```

**Returns**: `jwtToken` = `"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."`

---

#### 2.2: Validate JWT with User Service

**REST Call**: API Gateway → User Service

**HTTP Request**:
```http
POST http://user-service:8081/api/auth/validate HTTP/1.1
Content-Type: application/json

{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**User Service Processing**:
```java
// User Service: JwtService.validateToken()
public UserInfo validateToken(String token) {
    try {
        // Decode JWT using secret key
        Claims claims = Jwts.parser()
            .setSigningKey(jwtSecret) // HS256 secret key
            .parseClaimsJws(token)
            .getBody();

        // Extract user information from claims
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        List<String> roles = claims.get("roles", List.class);
        Date expiration = claims.getExpiration();

        // Check if token is expired
        if (expiration.before(new Date())) {
            throw new JwtExpiredException("Token expired");
        }

        // Return user info
        return new UserInfo(userId, email, roles);

    } catch (JwtException e) {
        throw new InvalidTokenException("Invalid token", e);
    }
}
```

**Execution**:
1. Parse JWT using HMAC-SHA256 secret key
2. Extract claims (userId, email, roles, expiry)
3. Verify signature
4. Check expiration: `expiration = 2024-02-16T14:30:00` vs `now = 2024-02-15T14:35:00` → Valid
5. Return user information

**Returns**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "roles": ["ROLE_PATIENT"],
  "valid": true
}
```

**Network Communication**:
- Protocol: HTTP/1.1
- Service Discovery: DNS lookup "user-service" → 192.168.1.101:8081
- Latency: ~5-10ms (within same data center)

---

#### 2.3: Route Request to Appointment Service

**Gateway Routing Configuration**:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: appointment-service
          uri: http://appointment-service:8083
          predicates:
            - Path=/api/appointments/**
          filters:
            - AddRequestHeader=X-User-Id, ${userId}
            - AddRequestHeader=X-User-Roles, ${roles}
```

**Forwarded Request**:
```http
POST http://appointment-service:8083/api/appointments HTTP/1.1
X-User-Id: 550e8400-e29b-41d4-a716-446655440000
X-User-Roles: ROLE_PATIENT
Content-Type: application/json

{
  "doctorId": "660e8400-e29b-41d4-a716-446655440001",
  "timeSlotId": "770e8400-e29b-41d4-a716-446655440002",
  "reasonForVisit": "Annual checkup"
}
```

**Note**: JWT is validated and user info is added as headers. Appointment Service doesn't need to validate JWT again.

---

### STEP 3: Appointment Service - Receive Request

**Component**: Appointment Service
**Port**: 8083

#### 3.1: Controller Entry Point

**File**: `AppointmentController.java` (hypothetical implementation)

```java
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<AppointmentDto> bookAppointment(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody BookAppointmentRequest request
    ) {
        // Log request
        log.info("Booking appointment for user: {}, slot: {}",
                userId, request.getTimeSlotId());

        // Call service layer
        AppointmentDto appointment = appointmentService
            .bookAppointment(userId, request);

        // Return response
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(appointment);
    }
}
```

**Input Parameters**:
- `userId` (from header): `550e8400-e29b-41d4-a716-446655440000`
- `request` (from body):
  ```java
  BookAppointmentRequest {
      doctorId: 660e8400-e29b-41d4-a716-446655440001,
      timeSlotId: 770e8400-e29b-41d4-a716-446655440002,
      reasonForVisit: "Annual checkup"
  }
  ```

**Returns**: Calls service layer method

---

### STEP 4: Lock Time Slot (Critical Synchronous Call)

**Component**: Appointment Service → Doctor Service
**Communication**: REST (Synchronous, blocking)

**Why Synchronous?**
- **Prevents double-booking**: Must lock slot before creating appointment
- **Strong consistency required**: Can't afford eventual consistency here
- **ACID semantics needed**: Either lock succeeds or booking fails

#### 4.1: REST Call to Doctor Service

**Method Called**: `DoctorServiceClient.lockTimeSlot()`

```java
// Appointment Service: DoctorServiceClient.java
@Service
public class DoctorServiceClient {

    private final RestTemplate restTemplate;
    private final String doctorServiceUrl = "http://doctor-service:8082";

    public TimeSlotDto lockTimeSlot(UUID timeSlotId, UUID appointmentId) {
        // Prepare request
        LockSlotRequest request = new LockSlotRequest(appointmentId);

        // Make synchronous HTTP call
        String url = doctorServiceUrl + "/api/slots/" + timeSlotId + "/lock";

        log.info("Locking time slot: {} for appointment: {}",
                timeSlotId, appointmentId);

        try {
            // POST http://doctor-service:8082/api/slots/{id}/lock
            ResponseEntity<TimeSlotDto> response = restTemplate.postForEntity(
                url,
                request,
                TimeSlotDto.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully locked time slot: {}", timeSlotId);
                return response.getBody();
            } else {
                throw new SlotLockFailedException("Failed to lock slot");
            }

        } catch (RestClientException e) {
            log.error("Error locking time slot: {}", e.getMessage());
            throw new ServiceCommunicationException(
                "Doctor service unavailable", e);
        }
    }
}
```

**HTTP Request**:
```http
POST http://doctor-service:8082/api/slots/770e8400-e29b-41d4-a716-446655440002/lock HTTP/1.1
Content-Type: application/json

{
  "appointmentId": "880e8400-e29b-41d4-a716-446655440003",
  "lockDuration": 300
}
```

**Network Details**:
- Protocol: HTTP/1.1
- Method: POST
- Timeout: 5 seconds (configured)
- Retry: None (fail fast for booking)

---

#### 4.2: Doctor Service - Process Lock Request

**Component**: Doctor Service
**Port**: 8082
**Database**: doctor_db

**Controller**:
```java
// Doctor Service: TimeSlotController.java
@PostMapping("/api/slots/{slotId}/lock")
@Transactional
public ResponseEntity<TimeSlotDto> lockTimeSlot(
        @PathVariable UUID slotId,
        @RequestBody LockSlotRequest request
) {
    log.info("Lock request for slot: {}", slotId);

    // Call service to lock slot
    TimeSlot slot = timeSlotService.lockSlot(slotId, request.getAppointmentId());

    // Convert to DTO and return
    return ResponseEntity.ok(convertToDto(slot));
}
```

**Service Method**:
```java
// Doctor Service: TimeSlotService.java
@Transactional
public TimeSlot lockSlot(UUID slotId, UUID appointmentId) {
    // 1. Fetch time slot with pessimistic lock
    TimeSlot slot = timeSlotRepository.findByIdWithLock(slotId)
        .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId));

    // 2. Validate slot is available
    if (!slot.isAvailable()) {
        throw new SlotNotAvailableException("Slot is already booked");
    }

    // 3. Check if slot is in the past
    if (slot.isPast()) {
        throw new InvalidSlotException("Cannot book past slot");
    }

    // 4. Lock the slot
    slot.setAvailable(false);
    slot.setLockedBy(appointmentId);
    slot.setLockedAt(LocalDateTime.now());

    // 5. Save to database
    TimeSlot savedSlot = timeSlotRepository.save(slot);

    log.info("Slot {} locked successfully for appointment {}",
            slotId, appointmentId);

    return savedSlot;
}
```

**Database Query 1** - Fetch with lock:
```sql
SELECT
    ts.id, ts.doctor_id, ts.slot_date, ts.start_time,
    ts.end_time, ts.is_available, ts.locked_by, ts.locked_at,
    ts.created_at, ts.updated_at
FROM time_slots ts
WHERE ts.id = '770e8400-e29b-41d4-a716-446655440002'
FOR UPDATE  -- Pessimistic lock
```

**Result**:
```java
TimeSlot {
    id: 770e8400-e29b-41d4-a716-446655440002,
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    slotDate: 2024-02-20,
    startTime: 10:00:00,
    endTime: 10:30:00,
    isAvailable: true,  // Currently available
    lockedBy: null,
    lockedAt: null
}
```

**Database Query 2** - Update slot:
```sql
UPDATE time_slots
SET is_available = false,
    locked_by = '880e8400-e29b-41d4-a716-446655440003',
    locked_at = '2024-02-15 14:35:23',
    updated_at = '2024-02-15 14:35:23'
WHERE id = '770e8400-e29b-41d4-a716-446655440002'
```

**Transaction Commit**:
```sql
COMMIT;
```

**Returns to Appointment Service**:
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "doctorId": "660e8400-e29b-41d4-a716-446655440001",
  "slotDate": "2024-02-20",
  "startTime": "10:00:00",
  "endTime": "10:30:00",
  "isAvailable": false,
  "locked": true,
  "lockedBy": "880e8400-e29b-41d4-a716-446655440003"
}
```

**Network Round-Trip Time**: ~10-15ms

---

### STEP 5: Create Appointment Record

**Component**: Appointment Service
**Database**: appointment_db

**Service Method**:
```java
// Appointment Service: AppointmentService.java
@Transactional
public AppointmentDto bookAppointment(UUID patientId, BookAppointmentRequest request) {

    // Step 4 completed: Slot is now locked

    // Step 5.1: Fetch patient details (for event publishing)
    UserInfo patient = userServiceClient.getUserInfo(patientId);
    // REST call to User Service
    // Returns: UserInfo {id, name, email, phone}

    // Step 5.2: Fetch doctor details (for event publishing)
    DoctorInfo doctor = doctorServiceClient.getDoctorInfo(request.getDoctorId());
    // REST call to Doctor Service
    // Returns: DoctorInfo {id, name, specialization, consultationFee, email}

    // Step 5.3: Create appointment entity
    Appointment appointment = Appointment.builder()
        .patientId(patientId)
        .doctorId(request.getDoctorId())
        .timeSlotId(request.getTimeSlotId())
        .status(AppointmentStatus.PENDING) // Initial status
        .reasonForVisit(request.getReasonForVisit())
        .build();

    // Step 5.4: Save to database
    Appointment savedAppointment = appointmentRepository.save(appointment);
    // Generated ID: 880e8400-e29b-41d4-a716-446655440003

    log.info("Appointment created: {}", savedAppointment.getId());

    // Continue to Step 6...
```

**Database Query** - Insert appointment:
```sql
INSERT INTO appointments (
    id, patient_id, doctor_id, time_slot_id,
    status, reason_for_visit, created_at, updated_at
) VALUES (
    '880e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440000',
    '660e8400-e29b-41d4-a716-446655440001',
    '770e8400-e29b-41d4-a716-446655440002',
    'PENDING',
    'Annual checkup',
    '2024-02-15 14:35:23',
    '2024-02-15 14:35:23'
)
```

**Appointment Created**:
```java
Appointment {
    id: 880e8400-e29b-41d4-a716-446655440003,
    patientId: 550e8400-e29b-41d4-a716-446655440000,
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    timeSlotId: 770e8400-e29b-41d4-a716-446655440002,
    status: PENDING,
    reasonForVisit: "Annual checkup",
    createdAt: 2024-02-15T14:35:23,
    updatedAt: 2024-02-15T14:35:23
}
```

---

### STEP 6: Publish Event to Kafka

**Component**: Appointment Service → Kafka
**Topic**: `appointment-created`
**Communication**: Asynchronous (Fire and forget)

#### 6.1: Create Event Object

**Location**: `shared/src/main/java/com/appointment/shared/event/AppointmentCreatedEvent.java`

```java
// Appointment Service: AppointmentService.java (continued)

    // Step 6.1: Build event with all necessary data
    AppointmentCreatedEvent event = new AppointmentCreatedEvent(
        // Appointment info
        savedAppointment.getId(),          // 880e8400-...

        // Patient info
        patient.getId(),                    // 550e8400-...
        patient.getEmail(),                 // john.doe@example.com
        patient.getName(),                  // John Doe
        patient.getPhone(),                 // +1234567890

        // Doctor info
        doctor.getId(),                     // 660e8400-...
        doctor.getName(),                   // Dr. Jane Smith
        doctor.getSpecialization(),         // Cardiology
        doctor.getEmail(),                  // dr.jane@hospital.com

        // Time slot info
        request.getTimeSlotId(),           // 770e8400-...
        slot.getSlotDate(),                // 2024-02-20
        slot.getStartTime(),               // 10:00:00
        slot.getEndTime(),                 // 10:30:00

        // Payment info
        doctor.getConsultationFee(),       // 150.00
        request.getReasonForVisit()        // Annual checkup
    );
```

**Event Constructor** (from `AppointmentCreatedEvent.java:79-115`):
```java
public AppointmentCreatedEvent(...parameters...) {
    // Initialize base event fields
    super.initializeEvent("AppointmentCreated");
    // This sets:
    //   - eventId: UUID.randomUUID() → 990e8400-e29b-41d4-a716-446655440004
    //   - timestamp: LocalDateTime.now() → 2024-02-15T14:35:23
    //   - eventType: "AppointmentCreated"
    //   - version: "1.0"

    // Set all appointment-specific fields
    this.appointmentId = appointmentId;
    this.patientId = patientId;
    // ... (all fields set)
}
```

**Event Object Created**:
```java
AppointmentCreatedEvent {
    // Base fields
    eventId: 990e8400-e29b-41d4-a716-446655440004,
    timestamp: 2024-02-15T14:35:23,
    eventType: "AppointmentCreated",
    version: "1.0",

    // Appointment fields
    appointmentId: 880e8400-e29b-41d4-a716-446655440003,
    patientId: 550e8400-e29b-41d4-a716-446655440000,
    patientEmail: "john.doe@example.com",
    patientName: "John Doe",
    patientPhone: "+1234567890",
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    doctorName: "Dr. Jane Smith",
    doctorSpecialization: "Cardiology",
    doctorEmail: "dr.jane@hospital.com",
    timeSlotId: 770e8400-e29b-41d4-a716-446655440002,
    appointmentDate: 2024-02-20,
    appointmentStartTime: 10:00:00,
    appointmentEndTime: 10:30:00,
    consultationFee: 150.00,
    reasonForVisit: "Annual checkup"
}
```

---

#### 6.2: Publish to Kafka

**Kafka Producer Configuration**:
```java
// Appointment Service: KafkaProducerConfig.java
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, BaseEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader acknowledgment
        config.put(ProducerConfig.RETRIES_CONFIG, 3); // Retry 3 times on failure
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, BaseEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

**Publishing Code**:
```java
// Appointment Service: EventPublisher.java
@Service
public class EventPublisher {

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;
    private static final String APPOINTMENT_CREATED_TOPIC = "appointment-created";

    public void publishAppointmentCreated(AppointmentCreatedEvent event) {
        log.info("Publishing AppointmentCreatedEvent: {}", event.getEventId());

        // Publish to Kafka topic
        // Key: appointmentId (ensures ordering for same appointment)
        // Value: event object (serialized to JSON)
        kafkaTemplate.send(
            APPOINTMENT_CREATED_TOPIC,                    // Topic
            event.getAppointmentId().toString(),          // Key
            event                                         // Value
        ).addCallback(
            success -> {
                // Callback on success
                log.info("Event published successfully: {} to partition {}",
                        event.getEventId(), success.getRecordMetadata().partition());
            },
            failure -> {
                // Callback on failure
                log.error("Failed to publish event: {}", event.getEventId(), failure);
                // In production: Retry, dead letter queue, or compensating action
            }
        );
    }
}
```

**Kafka Message**:

**Topic**: `appointment-created`
**Partition**: 2 (determined by key hash)
**Offset**: 1547
**Key**: `"880e8400-e29b-41d4-a716-446655440003"`
**Value** (JSON):
```json
{
  "eventId": "990e8400-e29b-41d4-a716-446655440004",
  "timestamp": "2024-02-15T14:35:23",
  "eventType": "AppointmentCreated",
  "version": "1.0",
  "appointmentId": "880e8400-e29b-41d4-a716-446655440003",
  "patientId": "550e8400-e29b-41d4-a716-446655440000",
  "patientEmail": "john.doe@example.com",
  "patientName": "John Doe",
  "patientPhone": "+1234567890",
  "doctorId": "660e8400-e29b-41d4-a716-446655440001",
  "doctorName": "Dr. Jane Smith",
  "doctorSpecialization": "Cardiology",
  "doctorEmail": "dr.jane@hospital.com",
  "timeSlotId": "770e8400-e29b-41d4-a716-446655440002",
  "appointmentDate": "2024-02-20",
  "appointmentStartTime": "10:00:00",
  "appointmentEndTime": "10:30:00",
  "consultationFee": 150.00,
  "reasonForVisit": "Annual checkup"
}
```

**Kafka Internal Processing**:
1. Serialize event to JSON (Jackson)
2. Compute partition: `hash(key) % num_partitions` → partition 2
3. Send to Kafka broker
4. Broker appends to commit log
5. Broker sends acknowledgment to producer
6. Event is now persisted and available to consumers

**Time Taken**: ~5-10ms (Kafka is very fast)

**Important**: This is **asynchronous**. The `send()` method returns immediately. The callback is executed in a separate thread.

---

### STEP 7: Return Response to Client

**Component**: Appointment Service → API Gateway → Client

#### 7.1: Prepare Response DTO

```java
// Appointment Service: AppointmentService.java (continued)

    // Step 6 completed: Event published to Kafka

    // Step 7.1: Convert entity to DTO
    AppointmentDto responseDto = AppointmentDto.builder()
        .id(savedAppointment.getId())
        .patientId(savedAppointment.getPatientId())
        .doctorId(savedAppointment.getDoctorId())
        .timeSlotId(savedAppointment.getTimeSlotId())
        .status(savedAppointment.getStatus())
        .reasonForVisit(savedAppointment.getReasonForVisit())
        .createdAt(savedAppointment.getCreatedAt())
        .build();

    log.info("Appointment booking completed successfully: {}",
            savedAppointment.getId());

    // Step 7.2: Commit transaction
    // @Transactional commits here
    // Database changes are permanent

    // Step 7.3: Return DTO
    return responseDto;
}
```

**DTO Object**:
```java
AppointmentDto {
    id: 880e8400-e29b-41d4-a716-446655440003,
    patientId: 550e8400-e29b-41d4-a716-446655440000,
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    timeSlotId: 770e8400-e29b-41d4-a716-446655440002,
    status: PENDING,
    reasonForVisit: "Annual checkup",
    createdAt: 2024-02-15T14:35:23
}
```

---

#### 7.2: Controller Returns HTTP Response

```java
// Appointment Service: AppointmentController.java (continued)

    // Service method returned
    AppointmentDto appointment = appointmentService
        .bookAppointment(userId, request);

    // Return HTTP 201 Created
    return ResponseEntity
        .status(HttpStatus.CREATED)  // 201
        .body(appointment);
}
```

**HTTP Response**:
```http
HTTP/1.1 201 Created
Content-Type: application/json
X-Response-Time: 45ms

{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "patientId": "550e8400-e29b-41d4-a716-446655440000",
  "doctorId": "660e8400-e29b-41d4-a716-446655440001",
  "timeSlotId": "770e8400-e29b-41d4-a716-446655440002",
  "status": "PENDING",
  "reasonForVisit": "Annual checkup",
  "createdAt": "2024-02-15T14:35:23"
}
```

---

#### 7.3: API Gateway Forwards Response

**API Gateway**:
- Receives response from Appointment Service
- Adds CORS headers
- Forwards to client

**Final HTTP Response to Client**:
```http
HTTP/1.1 201 Created
Content-Type: application/json
Access-Control-Allow-Origin: *
X-Response-Time: 50ms

{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "patientId": "550e8400-e29b-41d4-a716-446655440000",
  "doctorId": "660e8400-e29b-41d4-a716-446655440001",
  "timeSlotId": "770e8400-e29b-41d4-a716-446655440002",
  "status": "PENDING",
  "reasonForVisit": "Annual checkup",
  "createdAt": "2024-02-15T14:35:23"
}
```

**Client Receives Response**:
- Status: 201 Created
- Appointment ID: `880e8400-e29b-41d4-a716-446655440003`
- Status: PENDING
- **Total Time**: ~50ms

**User Experience**: User sees "Appointment booked successfully!" message immediately.

---

## Asynchronous Flow (Steps 8-15)

**IMPORTANT**: Everything below happens **AFTER** the user receives their response. The user is no longer waiting. These operations happen in the background.

---

### STEP 8: Payment Service - Consume Event

**Component**: Payment Service
**Port**: 8084
**Database**: payment_db

#### 8.1: Kafka Consumer Receives Event

**Consumer Configuration**:
```java
// Payment Service: KafkaConsumerConfig.java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, AppointmentCreatedEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.appointment.shared.event");
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
```

**Consumer Listener**:
```java
// Payment Service: AppointmentEventListener.java
@Component
public class AppointmentEventListener {

    private final PaymentService paymentService;

    @KafkaListener(
        topics = "appointment-created",
        groupId = "payment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleAppointmentCreated(AppointmentCreatedEvent event) {
        log.info("Received AppointmentCreatedEvent: {}", event.getEventId());

        try {
            // Process the event
            paymentService.createPaymentForAppointment(event);

            log.info("Payment created for appointment: {}",
                    event.getAppointmentId());

        } catch (Exception e) {
            log.error("Error processing appointment event: {}",
                    event.getEventId(), e);
            // In production: Retry, dead letter queue, alert
            throw e; // Kafka will retry based on configuration
        }
    }
}
```

**Kafka Polling**:
```
Time: 2024-02-15T14:35:23.100
Consumer polls Kafka broker every 100ms
New message available: offset 1547, partition 2
Deserialize JSON → AppointmentCreatedEvent object
Invoke listener method
```

**Event Received**:
```java
AppointmentCreatedEvent {
    eventId: 990e8400-e29b-41d4-a716-446655440004,
    appointmentId: 880e8400-e29b-41d4-a716-446655440003,
    // ... all event data ...
    consultationFee: 150.00
}
```

---

#### 8.2: Create Payment Record

**Service Method**:
```java
// Payment Service: PaymentService.java
@Transactional
public Payment createPaymentForAppointment(AppointmentCreatedEvent event) {

    // Step 8.2.1: Create payment entity
    Payment payment = Payment.builder()
        .appointmentId(event.getAppointmentId())
        .patientId(event.getPatientId())
        .doctorId(event.getDoctorId())
        .amount(event.getConsultationFee())
        .status(PaymentStatus.PENDING)
        .paymentMethod(null) // User hasn't chosen yet
        .build();

    // Step 8.2.2: Save to database
    Payment savedPayment = paymentRepository.save(payment);
    // Generated ID: aa0e8400-e29b-41d4-a716-446655440005

    log.info("Payment record created: {}", savedPayment.getId());

    // Step 8.2.3: Generate payment link
    String paymentLink = generatePaymentLink(savedPayment);
    // Example: https://payment.gateway.com/pay?ref=aa0e8400...

    savedPayment.setPaymentLink(paymentLink);
    paymentRepository.save(savedPayment);

    // Step 8.2.4: In production, send payment link to user
    // notificationService.sendPaymentLink(event.getPatientEmail(), paymentLink);

    return savedPayment;
}
```

**Database Query** - Insert payment:
```sql
INSERT INTO payments (
    id, appointment_id, patient_id, doctor_id,
    amount, status, payment_method, payment_link,
    created_at, updated_at
) VALUES (
    'aa0e8400-e29b-41d4-a716-446655440005',
    '880e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440000',
    '660e8400-e29b-41d4-a716-446655440001',
    150.00,
    'PENDING',
    NULL,
    'https://payment.gateway.com/pay?ref=aa0e8400...',
    '2024-02-15 14:35:23.200',
    '2024-02-15 14:35:23.200'
)
```

**Payment Created**:
```java
Payment {
    id: aa0e8400-e29b-41d4-a716-446655440005,
    appointmentId: 880e8400-e29b-41d4-a716-446655440003,
    patientId: 550e8400-e29b-41d4-a716-446655440000,
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    amount: 150.00,
    status: PENDING,
    paymentLink: "https://payment.gateway.com/pay?ref=aa0e8400...",
    createdAt: 2024-02-15T14:35:23.200
}
```

**Time**: ~100ms after event was published

---

### STEP 9: Notification Service - Send Confirmation Email

**Component**: Notification Service
**Port**: 8085
**Database**: notification_db

#### 9.1: Consume Event

**Consumer Listener**:
```java
// Notification Service: AppointmentEventListener.java
@Component
public class AppointmentEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "appointment-created",
        groupId = "notification-service"
    )
    public void handleAppointmentCreated(AppointmentCreatedEvent event) {
        log.info("Received AppointmentCreatedEvent for notification: {}",
                event.getEventId());

        try {
            // Send confirmation email
            notificationService.sendAppointmentConfirmation(event);

            // Send SMS (optional)
            if (event.getPatientPhone() != null) {
                notificationService.sendAppointmentSms(event);
            }

        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
            // Non-critical: Don't throw, just log
            // Notification failure shouldn't affect appointment
        }
    }
}
```

**Note**: Notification Service has a **different consumer group** (`notification-service`) than Payment Service (`payment-service`). This allows both services to consume the same event independently.

---

#### 9.2: Send Email

**Service Method**:
```java
// Notification Service: NotificationService.java
public void sendAppointmentConfirmation(AppointmentCreatedEvent event) {

    // Step 9.2.1: Build email content
    EmailTemplate template = emailTemplateService.getTemplate("appointment-confirmation");

    Map<String, Object> variables = Map.of(
        "patientName", event.getPatientName(),
        "doctorName", event.getDoctorName(),
        "specialization", event.getDoctorSpecialization(),
        "date", event.getAppointmentDate().format(DateTimeFormatter.ISO_DATE),
        "time", event.getAppointmentStartTime().format(DateTimeFormatter.ofPattern("h:mm a")),
        "appointmentId", event.getAppointmentId().toString()
    );

    String emailBody = template.render(variables);
    // Result: HTML email with appointment details

    // Step 9.2.2: Send email via external service
    EmailRequest emailRequest = EmailRequest.builder()
        .to(event.getPatientEmail())
        .subject("Appointment Confirmation - " + event.getDoctorName())
        .body(emailBody)
        .build();

    // Step 9.2.3: Call SendGrid/AWS SES API
    try {
        emailServiceProvider.send(emailRequest);
        log.info("Confirmation email sent to: {}", event.getPatientEmail());

        // Step 9.2.4: Log notification in database
        Notification notification = Notification.builder()
            .appointmentId(event.getAppointmentId())
            .recipientEmail(event.getPatientEmail())
            .type(NotificationType.APPOINTMENT_CONFIRMATION)
            .status(NotificationStatus.SENT)
            .sentAt(LocalDateTime.now())
            .build();

        notificationRepository.save(notification);

    } catch (EmailException e) {
        log.error("Failed to send email: {}", e.getMessage(), e);
        // Log failure but don't crash
    }
}
```

**External API Call** (SendGrid example):
```http
POST https://api.sendgrid.com/v3/mail/send HTTP/1.1
Authorization: Bearer SG.abc123...
Content-Type: application/json

{
  "personalizations": [{
    "to": [{"email": "john.doe@example.com"}]
  }],
  "from": {"email": "noreply@hospital.com"},
  "subject": "Appointment Confirmation - Dr. Jane Smith",
  "content": [{
    "type": "text/html",
    "value": "<html>Dear John Doe,<br>Your appointment with Dr. Jane Smith (Cardiology) is confirmed for February 20, 2024 at 10:00 AM...</html>"
  }]
}
```

**SendGrid Response**:
```http
HTTP/1.1 202 Accepted
X-Message-Id: filter0001.1234.5678.90AB

(Email queued for delivery)
```

**Database Query** - Log notification:
```sql
INSERT INTO notifications (
    id, appointment_id, recipient_email, type,
    status, sent_at, created_at
) VALUES (
    'bb0e8400-e29b-41d4-a716-446655440006',
    '880e8400-e29b-41d4-a716-446655440003',
    'john.doe@example.com',
    'APPOINTMENT_CONFIRMATION',
    'SENT',
    '2024-02-15 14:35:23.500',
    '2024-02-15 14:35:23.500'
)
```

**Time**: ~150ms after event was published

**User Experience**: User receives email within 1-2 seconds of booking (depending on email service)

---

### STEP 10: User Completes Payment (Simulated)

**Time**: ~5 minutes later (2024-02-15T14:40:23)

**User Action**:
1. User clicks payment link from email
2. User enters card details on payment gateway
3. User clicks "Pay Now"

**Payment Gateway**:
1. Processes payment with bank
2. Payment approved
3. Gateway calls webhook on Payment Service

**Webhook Call**:
```http
POST http://payment-service:8084/api/payments/webhook HTTP/1.1
Content-Type: application/json

{
  "transactionId": "txn_1234567890",
  "paymentId": "aa0e8400-e29b-41d4-a716-446655440005",
  "status": "SUCCESS",
  "amount": 150.00,
  "paymentMethod": "CARD",
  "cardLast4": "4242",
  "timestamp": "2024-02-15T14:40:23"
}
```

---

### STEP 11: Payment Service - Process Payment Success

**Component**: Payment Service
**Port**: 8084

**Webhook Controller**:
```java
// Payment Service: PaymentWebhookController.java
@PostMapping("/api/payments/webhook")
@Transactional
public ResponseEntity<Void> handlePaymentWebhook(
        @RequestBody PaymentWebhookRequest request
) {
    log.info("Received payment webhook for payment: {}", request.getPaymentId());

    // Step 11.1: Fetch payment record
    Payment payment = paymentRepository.findById(request.getPaymentId())
        .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));

    // Step 11.2: Validate payment status
    if (payment.getStatus() == PaymentStatus.COMPLETED) {
        log.warn("Payment already completed: {}", payment.getId());
        return ResponseEntity.ok().build(); // Idempotent
    }

    // Step 11.3: Update payment record
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setPaymentMethod(request.getPaymentMethod());
    payment.setTransactionId(request.getTransactionId());
    payment.setCompletedAt(LocalDateTime.now());

    Payment updatedPayment = paymentRepository.save(payment);
```

**Database Query** - Update payment:
```sql
UPDATE payments
SET status = 'COMPLETED',
    payment_method = 'CARD',
    transaction_id = 'txn_1234567890',
    completed_at = '2024-02-15 14:40:23',
    updated_at = '2024-02-15 14:40:23'
WHERE id = 'aa0e8400-e29b-41d4-a716-446655440005'
```

---

#### 11.2: Publish PaymentCompletedEvent

```java
// Payment Service: PaymentWebhookController.java (continued)

    // Step 11.4: Fetch related data for event
    Appointment appointment = appointmentServiceClient
        .getAppointment(payment.getAppointmentId());

    User patient = userServiceClient.getUser(payment.getPatientId());

    Doctor doctor = doctorServiceClient.getDoctor(payment.getDoctorId());

    // Step 11.5: Create PaymentCompletedEvent
    PaymentCompletedEvent event = new PaymentCompletedEvent(
        payment.getId(),                    // aa0e8400-...
        payment.getAppointmentId(),         // 880e8400-...
        payment.getAmount(),                // 150.00
        payment.getPaymentMethod(),         // CARD
        payment.getTransactionId(),         // txn_1234567890
        patient.getId(),                    // 550e8400-...
        patient.getEmail(),                 // john.doe@example.com
        patient.getName(),                  // John Doe
        doctor.getId(),                     // 660e8400-...
        doctor.getName()                    // Dr. Jane Smith
    );

    // Step 11.6: Publish event to Kafka
    eventPublisher.publishPaymentCompleted(event);

    log.info("PaymentCompletedEvent published: {}", event.getEventId());

    return ResponseEntity.ok().build();
}
```

**Event Object**:
```java
PaymentCompletedEvent {
    eventId: cc0e8400-e29b-41d4-a716-446655440007,
    timestamp: 2024-02-15T14:40:23,
    eventType: "PaymentCompleted",
    version: "1.0",
    paymentId: aa0e8400-e29b-41d4-a716-446655440005,
    appointmentId: 880e8400-e29b-41d4-a716-446655440003,
    amount: 150.00,
    paymentMethod: "CARD",
    transactionId: "txn_1234567890",
    patientId: 550e8400-e29b-41d4-a716-446655440000,
    patientEmail: "john.doe@example.com",
    patientName: "John Doe",
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    doctorName: "Dr. Jane Smith"
}
```

**Kafka Message**:
```json
Topic: payment-completed
Partition: 1
Offset: 823
Key: "880e8400-e29b-41d4-a716-446655440003"
Value: { ...PaymentCompletedEvent JSON... }
```

---

### STEP 12: Appointment Service - Confirm Appointment

**Component**: Appointment Service (as consumer)
**Port**: 8083

**Consumer Listener**:
```java
// Appointment Service: PaymentEventListener.java
@Component
public class PaymentEventListener {

    private final AppointmentService appointmentService;

    @KafkaListener(
        topics = "payment-completed",
        groupId = "appointment-service"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: {}", event.getEventId());

        try {
            // Update appointment status
            appointmentService.confirmAppointment(
                event.getAppointmentId(),
                event.getPaymentId()
            );

        } catch (Exception e) {
            log.error("Error confirming appointment: {}", e.getMessage(), e);
            throw e; // Retry
        }
    }
}
```

**Service Method**:
```java
// Appointment Service: AppointmentService.java
@Transactional
public void confirmAppointment(UUID appointmentId, UUID paymentId) {

    // Step 12.1: Fetch appointment
    Appointment appointment = appointmentRepository.findById(appointmentId)
        .orElseThrow(() -> new AppointmentNotFoundException(
            "Appointment not found: " + appointmentId));

    // Step 12.2: Check current status
    if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
        log.info("Appointment already confirmed: {}", appointmentId);
        return; // Idempotent
    }

    // Step 12.3: Update status to CONFIRMED
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setPaymentId(paymentId);
    appointment.setConfirmedAt(LocalDateTime.now());

    // Step 12.4: Save to database
    appointmentRepository.save(appointment);

    log.info("Appointment confirmed: {}", appointmentId);
}
```

**Database Query** - Update appointment:
```sql
UPDATE appointments
SET status = 'CONFIRMED',
    payment_id = 'aa0e8400-e29b-41d4-a716-446655440005',
    confirmed_at = '2024-02-15 14:40:23.500',
    updated_at = '2024-02-15 14:40:23.500'
WHERE id = '880e8400-e29b-41d4-a716-446655440003'
```

**Appointment Updated**:
```java
Appointment {
    id: 880e8400-e29b-41d4-a716-446655440003,
    patientId: 550e8400-e29b-41d4-a716-446655440000,
    doctorId: 660e8400-e29b-41d4-a716-446655440001,
    timeSlotId: 770e8400-e29b-41d4-a716-446655440002,
    status: CONFIRMED,  // Changed from PENDING
    paymentId: aa0e8400-e29b-41d4-a716-446655440005,
    confirmedAt: 2024-02-15T14:40:23.500,
    reasonForVisit: "Annual checkup",
    createdAt: 2024-02-15T14:35:23,
    updatedAt: 2024-02-15T14:40:23.500
}
```

**Time**: ~5 minutes after initial booking

---

### STEP 13: Notification Service - Send Payment Confirmation

**Component**: Notification Service
**Port**: 8085

**Consumer Listener**:
```java
// Notification Service: PaymentEventListener.java
@Component
public class PaymentEventListener {

    @KafkaListener(
        topics = "payment-completed",
        groupId = "notification-service"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent for notification: {}",
                event.getEventId());

        // Send payment confirmation email
        notificationService.sendPaymentConfirmation(event);

        // Send appointment confirmed SMS
        notificationService.sendAppointmentConfirmedSms(event);
    }
}
```

**Service Method**:
```java
// Notification Service: NotificationService.java
public void sendPaymentConfirmation(PaymentCompletedEvent event) {

    // Build email with payment receipt
    EmailTemplate template = emailTemplateService
        .getTemplate("payment-confirmation");

    Map<String, Object> variables = Map.of(
        "patientName", event.getPatientName(),
        "amount", event.getAmount(),
        "transactionId", event.getTransactionId(),
        "paymentMethod", event.getPaymentMethod(),
        "doctorName", event.getDoctorName()
    );

    String emailBody = template.render(variables);

    EmailRequest emailRequest = EmailRequest.builder()
        .to(event.getPatientEmail())
        .subject("Payment Confirmation - ₹" + event.getAmount())
        .body(emailBody)
        .build();

    emailServiceProvider.send(emailRequest);

    // Log notification
    Notification notification = Notification.builder()
        .appointmentId(event.getAppointmentId())
        .recipientEmail(event.getPatientEmail())
        .type(NotificationType.PAYMENT_CONFIRMATION)
        .status(NotificationStatus.SENT)
        .sentAt(LocalDateTime.now())
        .build();

    notificationRepository.save(notification);

    log.info("Payment confirmation sent to: {}", event.getPatientEmail());
}
```

**User Receives**:
- Email with payment receipt
- SMS confirming appointment is now confirmed

---

## Complete Code Trace Summary

### Timeline

```
T = 0ms
├─ User clicks "Book Appointment"
│
T = 5ms
├─ API Gateway receives request
├─ Validates JWT with User Service (10ms round-trip)
│
T = 15ms
├─ Forwards to Appointment Service
│
T = 20ms
├─ Appointment Service locks slot with Doctor Service
│  └─ REST call: 15ms round-trip
│  └─ Database lock acquired on doctor_db
│
T = 35ms
├─ Appointment Service creates appointment
│  └─ INSERT into appointment_db
│
T = 40ms
├─ Publishes AppointmentCreatedEvent to Kafka
│  └─ Kafka write: 5ms
│
T = 45ms
├─ Returns response to user
│  └─ HTTP 201 Created
│
═══════════════════════════════════════
  USER RECEIVES RESPONSE (50ms total)
═══════════════════════════════════════
│
T = 50ms
├─ Payment Service consumes event
│  └─ Creates payment record in payment_db
│
T = 50ms (parallel)
├─ Notification Service consumes event
│  └─ Sends confirmation email via SendGrid
│
T = 5 minutes later (user pays)
├─ Payment gateway calls webhook
│  └─ Payment Service updates payment status
│  └─ Publishes PaymentCompletedEvent
│
T = 5 minutes + 50ms
├─ Appointment Service consumes event
│  └─ Updates appointment status to CONFIRMED
│
T = 5 minutes + 50ms (parallel)
├─ Notification Service consumes event
│  └─ Sends payment confirmation email
```

---

## Event Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     MICROSERVICES WORKFLOW                       │
└─────────────────────────────────────────────────────────────────┘

[SYNCHRONOUS PHASE - User Waits]

Client
  │
  │ POST /api/appointments
  ↓
API Gateway (8080)
  │
  │ Validate JWT
  ├──[REST]──→ User Service (8081)
  │              │ Verify token
  │              │ Return user info
  │            ← ┘
  │
  │ Forward request
  ↓
Appointment Service (8083)
  │
  │ Lock time slot
  ├──[REST]──→ Doctor Service (8082)
  │              │ SELECT ... FOR UPDATE
  │              │ UPDATE is_available = false
  │              │ COMMIT
  │            ← ┘
  │
  │ Create appointment
  │ ├─→ INSERT into appointments (appointment_db)
  │ └─→ COMMIT
  │
  │ Publish event
  ├──[Kafka]──→ appointment-created topic
  │              │ Partition: 2
  │              │ Offset: 1547
  │            ← ┘ (ack received)
  │
  │ Return response
  ↓
Client (receives 201 Created) ✓

═══════════════════════════════════════════════════════════
[ASYNCHRONOUS PHASE - User No Longer Waiting]
═══════════════════════════════════════════════════════════

Kafka Topic: appointment-created
  │
  ├─────────────────┬──────────────────┐
  │                 │                  │
  ↓                 ↓                  ↓
Payment Service   Notification      (Future consumers)
(8084)            Service           - Analytics
  │               (8085)            - Reporting
  │                 │               - Audit
  │                 │
  │ Create payment  │ Send email
  │ record          │ (SendGrid API)
  ├─→ INSERT        │
  │   payment_db    ├─→ Email sent ✓
  │                 │
  │                 └─→ INSERT notification_db
  │
  │ (User pays via gateway - 5 min later)
  │
  │ Webhook from gateway
  │ UPDATE payment status
  ├─→ UPDATE payment_db
  │
  │ Publish event
  ├──[Kafka]──→ payment-completed topic
                  │
                  ├────────────┬─────────────┐
                  │            │             │
                  ↓            ↓             ↓
            Appointment    Notification   (Future)
            Service        Service
            (8083)         (8085)
              │              │
              │ Confirm      │ Send receipt
              │ appointment  │ email
              │              │
              └─→ UPDATE     └─→ Email sent ✓
                  appointment_db
                  status = CONFIRMED
```

---

## Comparison with Monolithic

| Aspect | Monolithic | Microservices |
|--------|-----------|---------------|
| **Number of Services** | 1 service | 6 services (Gateway, User, Doctor, Appointment, Payment, Notification) |
| **Databases** | 1 database | 5 separate databases |
| **Transactions** | ACID (all or nothing) | Eventual consistency (Saga pattern) |
| **Communication** | In-memory method calls | HTTP REST + Kafka events |
| **Latency (Success)** | ~45ms | ~50ms (user response) |
| **Latency (Full Flow)** | ~45ms | ~5 minutes (until confirmed) |
| **Deployment** | Single JAR | 6 containers + Kafka + 5 DBs |
| **Failure Isolation** | If one fails, all fails | Notification failure doesn't affect booking |
| **Scalability** | Scale entire app | Scale Payment Service independently |
| **Complexity** | Low | High |
| **Consistency** | Strong (ACID) | Eventual (async events) |
| **Code Lines** | ~2000 lines | ~5000 lines (across services) |
| **Infrastructure** | 1 server + 1 DB | 6 servers + 5 DBs + Kafka cluster |

---

## Failure Scenarios

### Scenario 1: Payment Service Down

**What Happens**:
1. User books appointment → SUCCESS ✓
2. Event published to Kafka → SUCCESS ✓
3. Payment Service is down → Event stays in Kafka
4. User receives booking confirmation
5. When Payment Service restarts → Processes backlog
6. Payment record created (delayed)

**Impact**: Minimal. Booking succeeds, payment processing delayed.

**Recovery**: Automatic (Kafka retains events)

---

### Scenario 2: Notification Service Down

**What Happens**:
1. User books appointment → SUCCESS ✓
2. Event published to Kafka → SUCCESS ✓
3. Payment Service processes → SUCCESS ✓
4. Notification Service down → No email sent
5. When Notification Service restarts → Sends backlog emails

**Impact**: No immediate impact. User doesn't get email immediately but gets it later.

**Recovery**: Automatic

---

### Scenario 3: Doctor Service Down (During Booking)

**What Happens**:
1. User books appointment
2. Appointment Service tries to lock slot
3. REST call to Doctor Service fails (timeout/connection error)
4. **Booking FAILS** ❌
5. User receives error: "Service temporarily unavailable"

**Impact**: User cannot book appointment. Must retry.

**Recovery**: Manual retry by user

**Why REST and not Kafka?**
- Slot locking must be synchronous
- Can't book without confirming slot availability
- Strong consistency required

---

### Scenario 4: Kafka Down

**What Happens**:
1. User books appointment
2. Slot locked → SUCCESS ✓
3. Appointment created → SUCCESS ✓
4. Publish to Kafka → **FAILS** ❌
5. Appointment Service throws exception
6. Transaction rolls back
7. Slot unlocked, appointment deleted
8. User receives error

**Impact**: Booking fails completely. Data consistent (no orphan records).

**Recovery**:
- If Kafka has replication: Automatic failover
- If total Kafka outage: Manual intervention needed

---

### Scenario 5: Duplicate Event Processing

**What Happens**:
1. Event published
2. Payment Service processes event
3. Payment Service crashes before committing offset
4. Kafka re-delivers event
5. Payment Service processes same event again

**Solution**: **Idempotency**
```java
public void createPaymentForAppointment(AppointmentCreatedEvent event) {
    // Check if payment already exists
    Optional<Payment> existing = paymentRepository
        .findByAppointmentId(event.getAppointmentId());

    if (existing.isPresent()) {
        log.info("Payment already exists, skipping: {}", event.getAppointmentId());
        return; // Idempotent
    }

    // Create payment...
}
```

**Impact**: No duplicate payments created.

---

## Key Takeaways

### Advantages of Microservices Architecture

1. **Fault Isolation**: Notification failure doesn't affect booking
2. **Independent Scaling**: Scale Payment Service during peak times
3. **Technology Freedom**: Use different languages/databases per service
4. **Team Autonomy**: Different teams own different services
5. **Async Benefits**: User doesn't wait for email/payment

### Challenges of Microservices Architecture

1. **Complexity**: More moving parts, harder to debug
2. **Eventual Consistency**: Data not immediately consistent across services
3. **Distributed Transactions**: Need Saga pattern for complex workflows
4. **Network Latency**: Inter-service calls add overhead
5. **Infrastructure Cost**: More servers, more databases, more monitoring

### When to Use Microservices

✅ **Use when**:
- Large team (50+ developers)
- Need to scale parts independently
- Different services have different scaling needs
- Want fault isolation
- Long-term project (10+ years)

❌ **Avoid when**:
- Small team (<10 developers)
- Simple application
- Strong consistency required everywhere
- Cost-sensitive
- MVP/prototype

---

## Conclusion

This document has traced **every single component call** in the microservices architecture for booking an appointment, showing:

- **Synchronous REST calls** for critical operations (JWT validation, slot locking)
- **Asynchronous Kafka events** for non-critical operations (notifications, payments)
- **Database operations** across multiple databases
- **Event publishing and consuming** with detailed message structures
- **Failure scenarios** and recovery strategies
- **Comparison with monolithic** architecture

The microservices architecture provides **scalability, resilience, and fault isolation** at the cost of **complexity, eventual consistency, and operational overhead**.

