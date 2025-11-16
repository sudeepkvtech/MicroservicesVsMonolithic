# Appointment Scheduling - Microservices Architecture

A production-ready microservices implementation using Spring Boot, Kafka, and REST APIs.

## Architecture Overview

This is a **microservices architecture** where the application is split into independent, deployable services.

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                               │
│              (Routing, Auth, Rate Limiting)                      │
└─────┬──────────┬──────────┬───────────┬──────────┬─────────────┘
      │          │          │           │          │
      ▼          ▼          ▼           ▼          ▼
┌──────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
│   User   │ │ Doctor │ │  Appt  │ │Payment │ │Notification│
│ Service  │ │Service │ │Service │ │Service │ │  Service   │
└────┬─────┘ └───┬────┘ └───┬────┘ └───┬────┘ └─────┬──────┘
     │           │           │          │            │
     │           │           │          │            │
     ▼           ▼           ▼          ▼            │
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │
│ User DB │ │Doctor DB│ │ Appt DB │ │ Pay DB  │    │
└─────────┘ └─────────┘ └─────────┘ └─────────┘    │
                                                     │
┌────────────────────────────────────────────────────┘
│              Apache Kafka (Event Bus)
│         ┌────────────────────────────┐
│         │ Topics:                    │
│         │ - appointment-created      │
│         │ - payment-completed        │
│         │ - appointment-cancelled    │
│         └────────────────────────────┘
└────────────────────────────────────────────────────┐
                                                     │
                            ┌────────────────────────┘
                            ▼
                    External Services:
                    - Email (SendGrid/SES)
                    - SMS (Twilio)
                    - Payment Gateway
```

## When to Use Kafka vs REST (Production Best Practices)

### ✅ Use Kafka (Asynchronous Events) For:

1. **Notifications**
   - Send appointment confirmation emails
   - Send cancellation notifications
   - SMS reminders
   - **Why**: Non-blocking, user doesn't wait for email to be sent

2. **State Changes Across Services**
   - Payment completed → Update appointment status
   - Appointment cancelled → Process refund
   - **Why**: Eventual consistency is acceptable, decouples services

3. **Multiple Consumers**
   - Appointment created → Payment Service + Notification Service
   - **Why**: One event, multiple reactions, loosely coupled

4. **Audit Trail**
   - All events are stored in Kafka for replay
   - **Why**: Event sourcing, debugging, analytics

### ❌ Use REST (Synchronous Calls) For:

1. **Authentication**
   - API Gateway → User Service
   - **Why**: Need immediate response, security critical

2. **Data Queries**
   - Get doctor details
   - Get available slots
   - **Why**: User expects immediate response, strong consistency needed

3. **Validation Before Action**
   - Check if slot is available before booking
   - **Why**: Must prevent double-booking, needs real-time lock

4. **Critical Business Logic**
   - Lock time slot during booking
   - **Why**: Cannot afford eventual consistency, need ACID transaction

## Service Breakdown

### 1. User Service
**Port**: 8081
**Database**: PostgreSQL (user_db)
**Responsibilities**:
- User registration
- Authentication (JWT)
- User profile management

**APIs** (REST):
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login (returns JWT)
- `GET /api/users/profile` - Get user profile
- `PUT /api/users/profile` - Update profile

**Kafka**: No events (authentication should be synchronous)

---

### 2. Doctor Service
**Port**: 8082
**Database**: PostgreSQL (doctor_db)
**Responsibilities**:
- Doctor profiles
- Time slot management
- Availability tracking

**APIs** (REST):
- `GET /api/doctors` - List doctors (with filters)
- `GET /api/doctors/{id}` - Get doctor details
- `GET /api/doctors/{id}/slots` - Get available slots
- `POST /api/doctors/{id}/slots` - Create time slots (doctor only)
- `POST /api/slots/{id}/lock` - Lock slot for booking (called by Appointment Service)

**Kafka**:
- Consumes: `appointment-cancelled` (to free up slot)

**Note**: Slot locking uses REST (synchronous) to prevent double-booking

---

### 3. Appointment Service
**Port**: 8083
**Database**: PostgreSQL (appointment_db)
**Responsibilities**:
- Create appointments
- Cancel appointments
- Manage appointment lifecycle

**APIs** (REST):
- `POST /api/appointments` - Book appointment
- `GET /api/appointments` - List user's appointments
- `GET /api/appointments/{id}` - Get appointment details
- `DELETE /api/appointments/{id}` - Cancel appointment

**Kafka**:
- **Publishes**:
  - `appointment-created` - After booking appointment
  - `appointment-cancelled` - After cancelling
- **Consumes**:
  - `payment-completed` - Update status to CONFIRMED

**Flow**:
1. Receives booking request (REST)
2. Calls Doctor Service to lock slot (REST - synchronous)
3. Creates appointment in database
4. Publishes `appointment-created` event (Kafka - async)
5. Returns success to user

---

### 4. Payment Service
**Port**: 8084
**Database**: PostgreSQL (payment_db)
**Responsibilities**:
- Process payments
- Handle refunds
- Payment status tracking

**APIs** (REST):
- `GET /api/payments/{id}` - Get payment status
- `POST /api/payments/{id}/verify` - Webhook from payment gateway

**Kafka**:
- **Consumes**:
  - `appointment-created` - Create payment record
  - `appointment-cancelled` - Process refund
- **Publishes**:
  - `payment-completed` - After successful payment

**Flow**:
1. Consumes `appointment-created` event
2. Creates payment record
3. Integrates with payment gateway (Stripe/Razorpay)
4. On success, publishes `payment-completed` event

---

### 5. Notification Service
**Port**: 8085
**Database**: PostgreSQL (notification_db) - for tracking sent notifications
**Responsibilities**:
- Send email notifications
- Send SMS notifications
- Notification history

**APIs** (REST):
- `GET /api/notifications` - Get user's notifications

**Kafka**:
- **Consumes**:
  - `appointment-created` - Send confirmation email
  - `payment-completed` - Send payment receipt
  - `appointment-cancelled` - Send cancellation email

**External Integrations**:
- SendGrid / AWS SES (Email)
- Twilio (SMS)

---

### 6. API Gateway
**Port**: 8080
**Responsibilities**:
- Route requests to appropriate services
- JWT validation
- Rate limiting
- Load balancing

**Technology**: Spring Cloud Gateway

---

## Event Flow Examples

### Scenario 1: Booking an Appointment

```
1. Client → API Gateway: POST /api/appointments
2. API Gateway → User Service: Validate JWT token (REST)
3. API Gateway → Appointment Service: Forward request
4. Appointment Service → Doctor Service: Lock slot (REST - synchronous!)
5. Appointment Service: Create appointment in DB
6. Appointment Service → Kafka: Publish AppointmentCreatedEvent
7. Appointment Service → Client: Return success
   ↓
8. Payment Service ← Kafka: Consume AppointmentCreatedEvent
9. Payment Service: Create payment record
10. Payment Service: Integrate with payment gateway
11. Payment Service → Kafka: Publish PaymentCompletedEvent
    ↓
12. Appointment Service ← Kafka: Consume PaymentCompletedEvent
13. Appointment Service: Update status to CONFIRMED
    ↓
14. Notification Service ← Kafka: Consume AppointmentCreatedEvent
15. Notification Service: Send confirmation email (async)
```

**Note**: Steps 8-15 happen asynchronously. User gets immediate response at step 7.

### Scenario 2: Cancelling an Appointment

```
1. Client → API Gateway: DELETE /api/appointments/{id}
2. API Gateway → Appointment Service: Forward request
3. Appointment Service: Update appointment status to CANCELLED
4. Appointment Service → Kafka: Publish AppointmentCancelledEvent
5. Appointment Service → Client: Return success
   ↓
6. Payment Service ← Kafka: Consume AppointmentCancelledEvent
7. Payment Service: Process refund
   ↓
8. Notification Service ← Kafka: Consume AppointmentCancelledEvent
9. Notification Service: Send cancellation email
   ↓
10. Doctor Service ← Kafka: Consume AppointmentCancelledEvent
11. Doctor Service: Free up the time slot
```

**Note**: Steps 6-11 happen asynchronously.

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.0 |
| API Gateway | Spring Cloud Gateway | 4.1.0 |
| Message Broker | Apache Kafka | 3.6.0 |
| Databases | PostgreSQL | 15 |
| Service Communication | REST + Kafka | - |
| Authentication | JWT | - |
| Containerization | Docker | Latest |
| Orchestration | Docker Compose | v2 |

## Kafka Topics

| Topic Name | Producers | Consumers | Purpose |
|-----------|-----------|-----------|---------|
| `appointment-created` | Appointment Service | Payment Service, Notification Service | Trigger payment and notifications |
| `payment-completed` | Payment Service | Appointment Service, Notification Service | Confirm appointment, send receipt |
| `appointment-cancelled` | Appointment Service | Payment Service, Notification Service, Doctor Service | Refund, notify, free slot |

## Database Per Service Pattern

Each service has its own database:
- **user_db** - User Service data
- **doctor_db** - Doctor and time slot data
- **appointment_db** - Appointment data
- **payment_db** - Payment records
- **notification_db** - Notification history

**Why**:
- Service independence
- Different scaling needs
- Technology flexibility
- Fault isolation

**Trade-off**:
- No ACID transactions across services
- Eventual consistency
- More complex queries

## Running the Services

### Start All Services
```bash
cd microservices
docker-compose up -d
```

This starts:
- 5 microservices
- 5 PostgreSQL databases
- Kafka + Zookeeper
- API Gateway

### View Logs
```bash
docker-compose logs -f appointment-service
```

### Stop All Services
```bash
docker-compose down
```

## Advantages of This Architecture

✅ **Independent Scaling** - Scale Payment Service separately during peak payment times
✅ **Technology Freedom** - Can use different databases/languages per service
✅ **Fault Isolation** - Notification failure doesn't affect booking
✅ **Team Autonomy** - Different teams can own different services
✅ **Deployment Independence** - Update Payment Service without touching others
✅ **Event-Driven Benefits** - Async processing, better user experience

## Challenges

❌ **Complexity** - More moving parts
❌ **Eventual Consistency** - Not all data is immediately consistent
❌ **Distributed Transactions** - Saga pattern needed for complex workflows
❌ **Debugging** - Harder to trace requests across services
❌ **Infrastructure Cost** - More resources needed
❌ **Network Latency** - Inter-service communication overhead

## Monitoring and Observability

### Health Checks
Each service exposes:
- `/actuator/health` - Service health
- `/actuator/metrics` - Metrics

### Distributed Tracing
- Add Spring Cloud Sleuth + Zipkin for request tracing
- Correlate logs across services

### Logging
- Centralized logging with ELK stack
- Structured JSON logs

## Next Steps (Production Readiness)

- [ ] Add service discovery (Consul/Eureka)
- [ ] Implement circuit breakers (Resilience4j)
- [ ] Add distributed tracing (Zipkin)
- [ ] Implement saga pattern for distributed transactions
- [ ] Add API rate limiting
- [ ] Implement retry logic with exponential backoff
- [ ] Add dead letter queues for failed events
- [ ] Implement event schema registry
- [ ] Add comprehensive monitoring (Prometheus + Grafana)
- [ ] Implement blue-green deployment

## Comparison with Monolithic

See main repository README for detailed comparison.

**TL;DR**:
- Monolithic: Simpler, faster initially, ACID transactions
- Microservices: Scalable, resilient, complex, eventual consistency
