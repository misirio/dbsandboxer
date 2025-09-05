# DbSandboxer

A Java library for isolated database testing using the template database pattern. Create fresh database copies for each test to ensure complete test isolation and reproducibility.

## Features

- **Test Isolation**: Each test runs in its own database copy
- **Fast Setup**: Uses PostgreSQL template databases for quick provisioning
- **Spring Boot Integration**: Seamless integration with Spring Boot tests via JUnit 5
- **Zero Test Pollution**: Tests never interfere with each other

## What It Does

DbSandboxer ensures each test runs with a clean database. Here's what happens:

```java
@SpringBootTest
@EnableDbSandboxer  // ← Activates database sandboxing
class ProductServiceTest {
    
    @Test
    void test1_addProduct() {
        // Starting with clean database + fixtures
        productRepo.save(new Product("Laptop", 999));
        assertThat(productRepo.count()).isEqualTo(4);  // 3 fixtures + 1 new
    }
    
    @Test
    void test2_verifyIsolation() {
        // Database is reset! The laptop from test1 doesn't exist
        assertThat(productRepo.count()).isEqualTo(3);  // Only fixtures
        assertThat(productRepo.findByName("Laptop")).isEmpty();
    }
}
```

**Without DbSandboxer**: test2 would fail because the laptop from test1 pollutes the database  
**With DbSandboxer**: Each test starts fresh, tests pass regardless of execution order


## Quick Start

### 1. Add Maven Dependency

```xml
<dependency>
    <groupId>io.misir</groupId>
    <artifactId>dbsandboxer-spring-boot-starter-test</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### 2. Configure Your Test DataSource

⚠️ **Important**: DbSandboxer requires `SimpleDriverDataSource` because it needs to drop and recreate databases. Connection pools like HikariCP will hold stale connections and cause errors.

Create `src/test/resources/application.yaml`:

```yaml
spring:
  datasource:
    # REQUIRED: Must use SimpleDriverDataSource (not HikariCP)
    type: org.springframework.jdbc.datasource.SimpleDriverDataSource
    
```

### 3. Enable DbSandboxer in Your Test

```java
@SpringBootTest
@EnableDbSandboxer  // ← Add this annotation
public class MyIntegrationTest {
    
    @Autowired
    private MyRepository repository;
    
    @Test
    void testDatabaseOperation() {
        // Each test gets a fresh database copy
        repository.save(new Entity());
        // Changes are isolated to this test only
    }
}
```

## How It Works

1. **Template Creation**: DbSandboxer creates a template database with your schema and test data
2. **Test Execution**: For each test, it recreates the database from the template
3. **Isolation**: Each test runs in complete isolation

Check out [ExampleIntegration1Test.java](examples/spring-boot-example) for a complete example with Liquibase and Testcontainers.

## Why Template Databases?

### ❌ Common Approaches & Their Problems

**@Transactional Tests**
- Rollback hides real behavior (triggers, constraints don't fire normally)
- Can't test multi-transaction scenarios
- Discouraged by Spring team - tests don't reflect production behavior

**Truncate/Delete After Each Test**
- Slow with many tables (cascading deletes, foreign keys)
- Error-prone - miss one table and tests fail
- Maintenance nightmare as schema grows

**In-Memory Databases (H2)**
- Different SQL dialect than production
- Missing PostgreSQL-specific features
- False positives/negatives in tests

### ✅ Template Database Approach (DbSandboxer)

- **Constant Speed**: ~50ms regardless of schema size (copies files, not data)
- **Real Database**: Tests run on actual PostgreSQL, catching real issues
- **Zero Maintenance**: No cleanup code, no table lists to maintain
- **Production-Like**: Triggers, constraints, procedures work exactly as in production


## Requirements

- Java 17 or higher
- PostgreSQL 12+
- Spring Boot 2.7+ (for Spring Boot integration)

## Modules

- **dbsandboxer-core**: Core API and PostgreSQL provider
- **dbsandboxer-spring-boot-starter-test**: Spring Boot test integration
- **examples**: Sample applications demonstrating usage

## Building from Source

```bash
mvn clean install
```

## Running Tests

```bash
mvn test
```

## Running the Example

The project includes a complete example demonstrating DbSandboxer with Spring Boot, JPA, and Liquibase.

### Prerequisites
- Docker (for running PostgreSQL via Testcontainers)
- Java 17+
- Maven 3.6+

### Running Example Tests

```bash
# Build the project
mvn clean package

# Run the example tests
cd examples/spring-boot-example
mvn test
```

The example demonstrates:
- Database schema migration with Liquibase
- Test fixture loading
- Complete test isolation - each test gets a fresh database
- JPA entity persistence and retrieval
- Verification that data changes don't affect other tests

Check out [ExampleIntegration1Test.java](examples/spring-boot-example/src/test/java/io/misir/dbsandboxer/examples/boot/ExampleIntegration1Test.java) to see how tests remain isolated.

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Author

[Fethullah Misir](https://github.com/misirio)