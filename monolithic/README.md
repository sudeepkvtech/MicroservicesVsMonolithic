# Appointment Scheduling Application - Monolithic Architecture

A comprehensive appointment scheduling system built with Spring Boot following SOLID principles and design patterns.

## Table of Contents
- [Architecture Overview](#architecture-overview)
- [Design Patterns Used](#design-patterns-used)
- [SOLID Principles](#solid-principles)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Setup and Installation](#setup-and-installation)
- [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
- [Database Schema](#database-schema)
- [Key Features](#key-features)

## Architecture Overview

This is a **monolithic application** where all components run as a single deployable unit.

```
┌─────────────────────────────────────┐
│         API Layer                   │
│    (Controllers + REST Endpoints)   │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│       Business Logic Layer          │
│         (Services)                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Data Access Layer              │
│      (Repositories + JPA)           │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      PostgreSQL Database            │
└─────────────────────────────────────┘
```

### Advantages of This Approach:
✅ **Simple to develop** - Single codebase, easy to understand
✅ **Simple to test** - All code in one place
✅ **Simple to deploy** - Single JAR file
✅ **ACID transactions** - Database transactions across all tables
✅ **Lower infrastructure cost** - One server, one database

### Disadvantages:
❌ **Tight coupling** - Changes in one module affect others
❌ **Scaling limitations** - Must scale entire application
❌ **Technology lock-in** - Entire app uses same tech stack
❌ **Long build times** - As application grows

## Design Patterns Used

### 1. **Repository Pattern**
- **Location**: `repository/` package
- **Purpose**: Abstracts data access logic
- **Example**: `UserRepository`, `AppointmentRepository`
- **Benefit**: Clean separation between business logic and data access

### 2. **Service Layer Pattern**
- **Location**: `service/` package
- **Purpose**: Encapsulates business logic
- **Example**: `AppointmentService`
- **Benefit**: Reusable business logic, easier testing

### 3. **DTO (Data Transfer Object) Pattern**
- **Location**: `dto/` package
- **Purpose**: Separate API representation from domain model
- **Example**: `UserDto`, `AppointmentDto`
- **Benefit**: Security (hide sensitive fields), versioning, flexibility

### 4. **Builder Pattern**
- **Implementation**: Using Lombok `@Builder`
- **Purpose**: Fluent object construction
- **Example**: `User.builder().firstName("John").build()`
- **Benefit**: Readable, immutable objects

### 5. **Template Method Pattern**
- **Implementation**: `BaseEntity` class
- **Purpose**: Common structure for all entities
- **Benefit**: DRY principle, consistent auditing

### 6. **State Pattern**
- **Implementation**: `AppointmentStatus`, `PaymentStatus`
- **Purpose**: Represent object states
- **Benefit**: Type-safe state management

### 7. **Facade Pattern**
- **Implementation**: Service methods
- **Purpose**: Simple interface for complex operations
- **Example**: `bookAppointment()` coordinates multiple repositories

### 8. **Optimistic Locking Pattern**
- **Implementation**: `@Version` in `TimeSlot`
- **Purpose**: Prevent double-booking
- **Benefit**: Handle concurrent bookings safely

### 9. **Dependency Injection Pattern**
- **Implementation**: Constructor injection with `@RequiredArgsConstructor`
- **Purpose**: Loose coupling
- **Benefit**: Testable, maintainable code

## SOLID Principles

### Single Responsibility Principle (SRP)
- **Entities**: Each entity represents one domain concept
- **Services**: Each service handles one area of business logic
- **Controllers**: Each controller handles one resource type

### Open/Closed Principle (OCP)
- **Extensibility**: New appointment statuses can be added without modifying existing code
- **DTOs**: Can create new DTOs for different views without changing entities

### Liskov Substitution Principle (LSP)
- **Repositories**: Can be replaced with mocks for testing
- **Services**: Can be extended without breaking existing functionality

### Interface Segregation Principle (ISP)
- **Repositories**: Focused interfaces with specific methods
- **Services**: Targeted service methods for specific use cases

### Dependency Inversion Principle (DIP)
- **Abstractions**: Services depend on Repository interfaces, not implementations
- **Injection**: Dependencies injected via constructor

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Programming language |
| Spring Boot | 3.2.0 | Application framework |
| Spring Data JPA | 3.2.0 | Data access |
| PostgreSQL | 15 | Database |
| Hibernate | 6.x | ORM |
| Lombok | 1.18.30 | Reduce boilerplate |
| MapStruct | 1.5.5 | DTO mapping |
| JWT | 0.12.3 | Authentication |
| Springdoc OpenAPI | 2.2.0 | API documentation |
| Maven | 3.9 | Build tool |
| Docker | Latest | Containerization |

## Project Structure

```
monolithic/
├── src/
│   ├── main/
│   │   ├── java/com/appointment/
│   │   │   ├── model/              # Domain entities
│   │   │   │   ├── BaseEntity.java          # Common entity fields
│   │   │   │   ├── User.java                # Patient entity
│   │   │   │   ├── Doctor.java              # Doctor entity
│   │   │   │   ├── TimeSlot.java            # Availability slot
│   │   │   │   ├── Appointment.java         # Appointment entity
│   │   │   │   └── Payment.java             # Payment entity
│   │   │   ├── repository/         # Data access layer
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── DoctorRepository.java
│   │   │   │   ├── TimeSlotRepository.java
│   │   │   │   ├── AppointmentRepository.java
│   │   │   │   └── PaymentRepository.java
│   │   │   ├── service/            # Business logic layer
│   │   │   │   ├── AppointmentService.java
│   │   │   │   ├── UserService.java
│   │   │   │   └── DoctorService.java
│   │   │   ├── dto/                # Data transfer objects
│   │   │   │   ├── UserDto.java
│   │   │   │   ├── AppointmentDto.java
│   │   │   │   └── PaymentDto.java
│   │   │   ├── controller/         # REST API endpoints
│   │   │   ├── security/           # Security configuration
│   │   │   ├── config/             # Application configuration
│   │   │   └── exception/          # Exception handlers
│   │   └── resources/
│   │       └── application.yml     # Application configuration
│   └── test/                       # Unit and integration tests
├── pom.xml                         # Maven dependencies
├── Dockerfile                      # Docker image definition
├── docker-compose.yml              # Docker Compose configuration
└── README.md                       # This file
```

## Setup and Installation

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose (for containerized deployment)
- PostgreSQL 15 (if running locally without Docker)

### Option 1: Run with Docker (Recommended)

1. **Clone the repository**
```bash
cd monolithic
```

2. **Start all services with Docker Compose**
```bash
docker-compose up -d
```

This will:
- Build the Spring Boot application
- Start PostgreSQL database
- Run database migrations
- Start the application on port 8080

3. **Check logs**
```bash
docker-compose logs -f app
```

4. **Access the application**
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/actuator/health

### Option 2: Run Locally

1. **Set up PostgreSQL**
```bash
# Create database
createdb appointment_db
```

2. **Update `application.yml`** with your PostgreSQL credentials

3. **Build the application**
```bash
mvn clean package
```

4. **Run the application**
```bash
java -jar target/appointment-monolithic-1.0.0.jar
```

Or using Maven:
```bash
mvn spring-boot:run
```

## Running the Application

### Start the Application
```bash
docker-compose up -d
```

### Stop the Application
```bash
docker-compose down
```

### Restart the Application
```bash
docker-compose restart app
```

### View Logs
```bash
docker-compose logs -f app
```

### Remove Everything (including data)
```bash
docker-compose down -v
```

## API Documentation

### Swagger UI
Once the application is running, access the interactive API documentation:
- **URL**: http://localhost:8080/swagger-ui.html

### Main API Endpoints

#### Authentication
```
POST /api/auth/register    - Register new user
POST /api/auth/login       - Login and get JWT token
```

#### Users
```
GET    /api/users/profile  - Get current user profile
PUT    /api/users/profile  - Update user profile
```

#### Doctors
```
GET    /api/doctors        - List all doctors (with filters)
GET    /api/doctors/{id}   - Get doctor details
GET    /api/doctors/{id}/slots - Get doctor's available slots
```

#### Appointments
```
POST   /api/appointments   - Book new appointment
GET    /api/appointments   - List user's appointments
GET    /api/appointments/{id} - Get appointment details
DELETE /api/appointments/{id} - Cancel appointment
```

#### Payments
```
POST   /api/payments       - Initiate payment
GET    /api/payments/{id}  - Get payment status
```

## Database Schema

### Entity Relationship Diagram
```
┌──────────┐         ┌─────────────┐         ┌──────────┐
│   User   │1      N │ Appointment │N      1 │  Doctor  │
│          ├─────────┤             ├─────────┤          │
└──────────┘         └──────┬──────┘         └────┬─────┘
                            │1                    │1
                            │                     │
                            │1                    │N
                     ┌──────┴──────┐       ┌──────┴──────┐
                     │   Payment   │       │  TimeSlot   │
                     └─────────────┘       └─────────────┘
```

### Tables
- **users** - Patient information
- **doctors** - Doctor profiles
- **time_slots** - Doctor availability
- **appointments** - Scheduled appointments
- **payments** - Payment records
- **user_roles** - User role mappings

## Key Features

### ✅ Implemented
- User registration and authentication
- Doctor profile management
- Time slot creation and management
- Appointment booking with validation
- Double-booking prevention (optimistic locking)
- Payment tracking
- Comprehensive logging
- API documentation
- Docker containerization
- Database migrations

### 🚧 To Be Implemented
- JWT authentication filter
- REST controllers
- Password encryption
- Email notifications
- Payment gateway integration
- Search functionality
- File uploads (doctor certificates)
- Appointment reminders

## Code Quality Features

### Comprehensive Comments
Every line of code is commented to explain:
- **What** it does
- **Why** it's done that way
- **How** it works
- Which design patterns are used

### Design Patterns
See [Design Patterns Used](#design-patterns-used) section above.

### SOLID Principles
See [SOLID Principles](#solid-principles) section above.

### Best Practices
- Constructor injection (immutable dependencies)
- Lombok to reduce boilerplate
- Builder pattern for object creation
- Optional for null safety
- Stream API for functional programming
- Transaction management
- Exception handling
- Validation at DTO level
- Separation of concerns

## Comparison with Microservices

| Aspect | Monolithic (This App) | Microservices |
|--------|----------------------|---------------|
| Deployment | Single JAR | Multiple services |
| Scaling | Vertical | Horizontal (per service) |
| Technology | Single stack | Polyglot |
| Transactions | ACID | Eventual consistency |
| Complexity | Low | High |
| Team Size | Small | Large |
| Build Time | Fast initially | Always fast |
| Debugging | Easy | Difficult |

## Development Guidelines

### Adding a New Feature
1. Create entity in `model/` package
2. Create repository in `repository/` package
3. Create DTOs in `dto/` package
4. Create service in `service/` package
5. Create controller in `controller/` package
6. Add tests
7. Update documentation

### Coding Standards
- Follow SOLID principles
- Use design patterns appropriately
- Comment every line explaining purpose
- Write unit tests for services
- Use meaningful variable names
- Follow Spring Boot conventions

## Troubleshooting

### Application won't start
```bash
# Check if PostgreSQL is running
docker-compose ps

# Check PostgreSQL logs
docker-compose logs postgres

# Check application logs
docker-compose logs app
```

### Database connection errors
- Verify PostgreSQL is healthy: `docker-compose ps`
- Check credentials in `application.yml`
- Ensure database exists

### Port already in use
```bash
# Change port in application.yml or docker-compose.yml
# Or stop process using port 8080
lsof -ti:8080 | xargs kill -9
```

## License
This is an educational project for comparing architectures.

## Contact
For questions or issues, please refer to the main repository documentation.
