# Microservices vs Monolithic Architecture

A comprehensive, production-realistic comparison of Monolithic and Microservices architectures using a real-world **Appointment Scheduling Application** built with Java, Spring Boot, and Kafka.

## 🎯 Project Overview

This project demonstrates **both architectural patterns** side-by-side with the same application, allowing you to:
- **Compare** implementation complexity
- **Understand** when to use each pattern
- **Learn** production best practices
- **See** real code with comprehensive comments

### What's Built

An **Appointment Scheduling System** for patients to book appointments with doctors.

**Features**:
- User registration and authentication
- Doctor profiles and availability management
- Appointment booking with validation
- Payment processing
- Email/SMS notifications
- Search functionality

---

## 📁 Repository Structure

```
MicroservicesVsMonolithic/
├── DESIGN.md                          # Low-level design document
├── ARCHITECTURE_COMPARISON.md         # Detailed comparison (READ THIS!)
├── monolithic/                        # Monolithic implementation
│   ├── src/main/java/com/appointment/
│   │   ├── model/                     # Domain entities (User, Doctor, Appointment, etc.)
│   │   ├── repository/                # Data access layer
│   │   ├── service/                   # Business logic
│   │   ├── dto/                       # Data transfer objects
│   │   └── config/                    # Configuration
│   ├── pom.xml                        # Maven dependencies
│   ├── Dockerfile                     # Container image
│   ├── docker-compose.yml             # Run with PostgreSQL
│   └── README.md                      # Detailed monolithic docs
└── microservices/                     # Microservices implementation
    ├── shared/                        # Common events and DTOs
    │   └── src/main/java/com/appointment/shared/event/
    │       ├── BaseEvent.java         # Event base class
    │       ├── AppointmentCreatedEvent.java
    │       ├── PaymentCompletedEvent.java
    │       └── AppointmentCancelledEvent.java
    ├── user-service/                  # Authentication & users
    ├── doctor-service/                # Doctors & availability
    ├── appointment-service/           # Core booking logic
    ├── payment-service/               # Payment processing
    ├── notification-service/          # Email/SMS notifications
    ├── api-gateway/                   # API Gateway (entry point)
    ├── docker-compose.yml             # All services + Kafka
    └── README.md                      # Detailed microservices docs
```

---

## 🏗️ Architecture Comparison

### Monolithic Architecture

```
┌─────────────────────────────────────┐
│     Single Spring Boot Application  │
│  ┌──────────────────────────────┐  │
│  │   All Services Together       │  │
│  │   - UserService               │  │
│  │   - DoctorService             │  │
│  │   - AppointmentService        │  │
│  │   - PaymentService            │  │
│  │   - NotificationService       │  │
│  └──────────────────────────────┘  │
└────────────┬────────────────────────┘
             │
       ┌─────▼─────┐
       │ PostgreSQL │
       └───────────┘
```

**Characteristics**:
- ✅ Simple to develop and deploy
- ✅ ACID transactions across all tables
- ✅ Fast in-memory method calls
- ❌ Must scale entire application
- ❌ Single point of failure
- ❌ Technology lock-in

### Microservices Architecture

```
           ┌────────────────┐
           │   API Gateway   │
           └───────┬─────────┘
                   │
      ┌────────────┴────────────┐
      ▼            ▼            ▼
 ┌────────┐   ┌────────┐   ┌────────┐
 │  User  │   │ Doctor │   │  Appt  │
 │ Service│   │Service │   │Service │
 └───┬────┘   └───┬────┘   └───┬────┘
     │            │            │
     ▼            ▼            ▼
  ┌─────┐      ┌─────┐      ┌─────┐
  │ DB  │      │ DB  │      │ DB  │
  └─────┘      └─────┘      └─────┘

           ┌──────────────────┐
           │  Kafka (Events)  │
           │  - appointment-created
           │  - payment-completed
           └──────────────────┘
```

**Characteristics**:
- ✅ Independent scaling
- ✅ Fault isolation
- ✅ Technology flexibility
- ✅ Team autonomy
- ❌ Complex deployment
- ❌ Eventual consistency
- ❌ Distributed system complexity

---

## 🚀 Quick Start

### Monolithic Application

```bash
# Navigate to monolithic directory
cd monolithic

# Start with Docker Compose (includes PostgreSQL)
docker-compose up -d

# Access the application
# API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Health: http://localhost:8080/actuator/health

# View logs
docker-compose logs -f app

# Stop
docker-compose down
```

### Microservices Application

```bash
# Navigate to microservices directory
cd microservices

# Start all services (6 services + Kafka + 5 databases)
docker-compose up -d

# Access points
# API Gateway: http://localhost:8080
# Kafka UI: http://localhost:8090
# User Service: http://localhost:8081
# Doctor Service: http://localhost:8082
# Appointment Service: http://localhost:8083
# Payment Service: http://localhost:8084
# Notification Service: http://localhost:8085

# View logs for a specific service
docker-compose logs -f appointment-service

# Stop all services
docker-compose down
```

---

## 📚 Key Learning Points

### 1. When to Use Kafka (Event-Driven)

**✅ Use for**:
- Sending notifications (async, non-blocking)
- Cross-service state updates (eventual consistency OK)
- Multiple consumers need same event
- Audit trail requirements

**Example**:
```java
// After booking appointment
appointmentService.book() {
    appointment = repository.save(appointment);

    // Publish event (async) - user doesn't wait for email!
    kafka.publish(new AppointmentCreatedEvent(...));

    return appointment; // Immediate response
}

// Notification Service listens and sends email async
@KafkaListener("appointment-created")
void onAppointmentCreated(AppointmentCreatedEvent event) {
    emailService.send(...);
}
```

### 2. When to Use REST (Synchronous)

**✅ Use for**:
- Authentication (need immediate response)
- Validation (prevent double-booking)
- Real-time queries (get doctor details)
- Critical operations (strong consistency)

**Example**:
```java
// Must check slot availability NOW
appointmentService.book() {
    // Synchronous REST call to Doctor Service
    SlotResponse slot = doctorServiceClient.checkSlot(slotId);

    if (!slot.isAvailable()) {
        throw new SlotNotAvailableException();
    }

    // Lock slot (also synchronous)
    doctorServiceClient.lockSlot(slotId);
}
```

### 3. Transaction Handling

**Monolithic** (Simple ACID):
```java
@Transactional // Automatic rollback if anything fails
public void bookAppointment() {
    appointmentRepository.save(appointment);
    paymentRepository.save(payment);
    slotRepository.update(slot);
    // All or nothing!
}
```

**Microservices** (Saga Pattern):
```java
public void bookAppointment() {
    // 1. Create appointment (local DB)
    appointment = repository.save(appointment);

    // 2. Publish event (async)
    kafka.publish(new AppointmentCreatedEvent());

    // 3. Payment Service listens and processes
    // 4. If payment fails, compensate:
}

@KafkaListener("payment-failed")
void compensate(PaymentFailedEvent event) {
    // Rollback by cancelling appointment
    appointmentService.cancel(event.getAppointmentId());
}
```

---

## 🎓 Design Patterns & Principles

### Monolithic Patterns
1. **Repository Pattern** - Data access abstraction
2. **Service Layer Pattern** - Business logic encapsulation
3. **DTO Pattern** - Separate API from domain model
4. **Builder Pattern** - Fluent object construction
5. **Template Method** - BaseEntity with common fields
6. **Optimistic Locking** - Prevent double-booking

### Microservices Patterns
1. **Database per Service** - Service independence
2. **API Gateway** - Single entry point
3. **Event-Driven Architecture** - Async communication via Kafka
4. **Saga Pattern** - Distributed transactions
5. **Circuit Breaker** - Fault tolerance (mentioned, not implemented)
6. **Service Discovery** - Dynamic service location (mentioned)

### SOLID Principles (Both)
- **Single Responsibility**: Each class/service has one purpose
- **Open/Closed**: Open for extension, closed for modification
- **Liskov Substitution**: Services/repos can be mocked
- **Interface Segregation**: Focused interfaces
- **Dependency Inversion**: Depend on abstractions

---

## 📊 Detailed Comparison

See **[ARCHITECTURE_COMPARISON.md](ARCHITECTURE_COMPARISON.md)** for:
- When to use each architecture
- Detailed pros/cons with examples
- Real-world scenarios
- Migration strategies
- Code comparisons
- Scaling analysis
- Cost breakdown

---

## 🛠️ Technology Stack

| Component | Monolithic | Microservices |
|-----------|-----------|---------------|
| Language | Java 17 | Java 17 |
| Framework | Spring Boot 3.2 | Spring Boot 3.2 |
| Database | PostgreSQL 15 (single) | PostgreSQL 15 (per service) |
| Message Broker | - | Apache Kafka 3.6 |
| API Gateway | - | Spring Cloud Gateway |
| Containerization | Docker | Docker |
| Orchestration | Docker Compose | Docker Compose |
| Build Tool | Maven | Maven |

---

## 📖 Documentation

Each directory has detailed documentation:

- **[monolithic/README.md](monolithic/README.md)** - Monolithic implementation details
- **[microservices/README.md](microservices/README.md)** - Microservices implementation details
- **[DESIGN.md](DESIGN.md)** - Initial low-level design document
- **[ARCHITECTURE_COMPARISON.md](ARCHITECTURE_COMPARISON.md)** - Comprehensive comparison

---

## 🎯 Use Cases

### Choose Monolithic If:
- ✅ Team size < 10 developers
- ✅ Startup/MVP (need speed to market)
- ✅ Simple domain
- ✅ Predictable load
- ✅ Limited budget
- ✅ ACID transactions essential

### Choose Microservices If:
- ✅ Team size > 20 developers
- ✅ Complex domain (clear service boundaries)
- ✅ Need independent scaling
- ✅ High availability critical
- ✅ Different tech stacks needed
- ✅ Multiple teams working independently

### The Reality:
**Most applications should start monolithic and migrate to microservices when pain points emerge.**

Famous examples:
- Netflix, Amazon, Uber, Airbnb all started monolithic!

---

## 🔍 Code Quality Features

### Comprehensive Comments
Every line of code is commented explaining:
- **What** it does
- **Why** it's designed that way
- **Which** design pattern is used
- **How** it works

### Educational Focus
- Design pattern explanations
- SOLID principle demonstrations
- Best practice examples
- Production considerations
- Common pitfalls highlighted

---

## 📈 Learning Path

1. **Read** [DESIGN.md](DESIGN.md) - Understand requirements
2. **Explore** monolithic/ - See simple implementation
3. **Run** monolithic with Docker Compose
4. **Explore** microservices/ - See distributed implementation
5. **Run** microservices with Docker Compose
6. **Compare** - Use [ARCHITECTURE_COMPARISON.md](ARCHITECTURE_COMPARISON.md)
7. **Experiment** - Try breaking things, see how each handles failures

---

## 🤝 Key Takeaways

1. **No Silver Bullet** - Both architectures have trade-offs
2. **Start Simple** - Monolithic is often the right choice initially
3. **Migrate When Needed** - Move to microservices when you feel the pain
4. **Kafka ≠ Always** - Use async events only when appropriate
5. **Understand Trade-offs** - ACID vs Eventual Consistency
6. **Team Matters** - Architecture choice depends on team size/skill

---

## 📝 Next Steps (Future Enhancements)

- [ ] Add JWT authentication implementation
- [ ] Complete REST controllers for all services
- [ ] Add integration tests
- [ ] Implement circuit breaker pattern (Resilience4j)
- [ ] Add distributed tracing (Zipkin/Jaeger)
- [ ] Implement service discovery (Consul/Eureka)
- [ ] Add API rate limiting
- [ ] Implement dead letter queues
- [ ] Add monitoring (Prometheus + Grafana)
- [ ] Kubernetes deployment manifests

---

## 📚 References

- Spring Boot Documentation
- Apache Kafka Documentation
- Microservices Patterns (Chris Richardson)
- Domain-Driven Design (Eric Evans)
- Building Microservices (Sam Newman)

---

## 🙏 Acknowledgments

This project is built for educational purposes to help developers understand the practical differences between monolithic and microservices architectures through real, production-like code.

---

## 📄 License

Educational project - Free to use and learn from.

---

## 🔗 Quick Links

- [Detailed Design](DESIGN.md)
- [Architecture Comparison](ARCHITECTURE_COMPARISON.md)
- [Monolithic README](monolithic/README.md)
- [Microservices README](microservices/README.md)
