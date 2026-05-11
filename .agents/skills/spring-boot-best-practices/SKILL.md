---
name: spring-boot-best-practices
description: Java and Spring Boot coding rules and implementation best practices.
---

# Java + Spring Boot — Rules & Best Practices
---

## 1. Project Structure

```
src/
  main/
    java/com/company/appname/
      config/          # @Configuration classes, beans, security config
      controller/      # @RestController — HTTP layer only
      service/         # @Service — business logic
      repository/      # @Repository — data access (JPA/JDBC)
      domain/          # Entities, value objects
      dto/             # Request/response DTOs (never expose entities directly)
      exception/       # Custom exceptions + global handler
      util/            # Pure static helpers (no Spring beans)
    resources/
      application.yml  # Prefer YAML over .properties
      application-dev.yml
      application-prod.yml
  test/
    java/com/company/appname/
      controller/      # @WebMvcTest slices
      service/         # Unit tests (Mockito)
      repository/      # @DataJpaTest slices
      integration/     # @SpringBootTest full context
```

**Rules:**

- One class per file, matching the filename.
- Package by layer (not by feature) for small apps; package by feature for large ones.
- Never put business logic in controllers or entities.

---

## 2. Dependency Injection

```java
// CORRECT — constructor injection (testable, immutable, no reflection tricks)
@Service
@RequiredArgsConstructor          // Lombok — generates constructor for final fields
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
}

// WRONG — field injection (hides dependencies, breaks unit tests)
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository; // ← don't do this
}
```

**Rules:**

- Always use constructor injection.
- Use `@RequiredArgsConstructor` (Lombok) to reduce boilerplate.
- Mark injected fields `final`.
- Avoid `@Autowired` on fields or setters.

---

## 3. REST Controllers

```java

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        URI location = URI.create("/api/v1/orders/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Rules:**

- Controllers do zero business logic — delegate everything to services.
- Always version APIs (`/api/v1/`).
- Use `ResponseEntity` to control status codes explicitly.
- Validate input with `@Valid` + Bean Validation (`@NotNull`, `@Size`, etc.).
- Return DTOs, never JPA entities.
- Use proper HTTP verbs and status codes (201 Created, 204 No Content, 404 Not Found).

---

## 4. DTOs

```java
// Prefer Java records for immutable DTOs (Java 16+)
public record CreateOrderRequest(
                @NotNull @Positive Long productId,
                @NotNull @Min(1) Integer quantity
        ) {
}

public record OrderResponse(Long id, String status, BigDecimal total) {
}
```

**Rules:**

- Separate request DTOs from response DTOs.
- Use records (Java 16+) for immutability.
- Validate request DTOs with Bean Validation annotations.
- Never return entity objects from controllers — always map to a DTO.
- Use MapStruct for mapping (avoid manual getters/setters at scale).

---

## 5. Service Layer

```java

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // default read-only; override per method
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderResponse findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toResponse(order);
    }

    @Transactional                        // write operations override the class default
    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order(request.productId(), request.quantity());
        return toResponse(orderRepository.save(order));
    }
}
```

**Rules:**

- Annotate the class with `@Transactional(readOnly = true)` and override write methods.
- Never call one `@Transactional` method from another inside the same bean (proxy bypass).
- Throw domain-specific exceptions (not generic `RuntimeException`).
- Keep services focused — one service per aggregate/domain concept.

---

## 6. Persistence (JPA / Spring Data)

```java

@Entity
@Table(name = "orders")
@Getter
@Setter                          // Lombok — but avoid @Data on entities
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA requires no-arg ctor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)         // always STRING, never ORDINAL
    private OrderStatus status;

    // equals/hashCode based on natural key or id — be explicit
}
```

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Prefer derived query methods or @Query over native SQL
    List<Order> findByStatusAndProductId(OrderStatus status, Long productId);

    @Query("SELECT o FROM Order o WHERE o.status = :status")
    Page<Order> findByStatus(@Param("status") OrderStatus status, Pageable pageable);
}
```

**Rules:**

- Never use `@Data` on JPA entities (breaks `equals`/`hashCode`/`toString` with lazy loading).
- Always use `EnumType.STRING`.
- Prefer `GenerationType.IDENTITY` for MySQL/PostgreSQL; `SEQUENCE` for Oracle.
- Use `Pageable` for any list endpoint that could grow.
- Avoid N+1 queries — use `JOIN FETCH` or `@EntityGraph`.
- Never expose entities through the API layer.

---

## 7. Exception Handling

```java
// Custom exception
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id: " + id);
    }
}

// Global handler
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Log full stack trace here — don't expose it in the response
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Internal server error", "INTERNAL_ERROR"));
    }
}
```

**Rules:**

- One `@RestControllerAdvice` global handler — not scattered `try/catch` in controllers.
- Never leak stack traces or internal details to the client.
- Map every custom exception to a specific HTTP status code.
- Log the full exception server-side.

---

## 8. Configuration & Properties

```yaml
# application.yml
spring:
  datasource:
    url: ${DB_URL}                    # always externalize secrets via env vars
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false               # always disable — causes lazy-loading anti-pattern
    hibernate:
      ddl-auto: validate              # never 'create' or 'update' in prod; use Flyway/Liquibase

server:
  port: 8080

app:
  feature-flags:
    new-checkout: false
```

```java

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(FeatureFlags featureFlags) {
    public record FeatureFlags(@NotNull Boolean newCheckout) {
    }
}
```

**Rules:**

- Externalize all secrets and env-specific config via environment variables.
- Use `@ConfigurationProperties` over `@Value` for grouped config.
- Always set `spring.jpa.open-in-view=false`.
- Never use `ddl-auto: create` or `update` in production; use Flyway or Liquibase.
- Use per-environment profiles (`application-dev.yml`, `application-prod.yml`).

---

## 9. Security Basics

```java

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())                     // disable for stateless REST
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();                   // never MD5/SHA1
    }
}
```

**Rules:**

- Use BCrypt for password hashing — never MD5, SHA-1, or plain text.
- Stateless REST APIs → disable sessions and CSRF.
- Use JWT or OAuth2 (Spring Security OAuth2 Resource Server).
- Validate and sanitize all inputs (Bean Validation + parameterized queries).
- Never log passwords, tokens, or PII.
- Use HTTPS in production — configure via reverse proxy or `server.ssl.*`.

---

## 10. Testing

```java
// Unit test — no Spring context
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @InjectMocks
    OrderService orderService;

    @Test
    void findById_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> orderService.findById(99L));
    }
}

// Controller slice test
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    OrderService orderService;

    @Test
    void getOrder_returnsOk() throws Exception {
        when(orderService.findById(1L)).thenReturn(new OrderResponse(1L, "PENDING", BigDecimal.TEN));
        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

// Repository slice test
@DataJpaTest
class OrderRepositoryTest {
    @Autowired
    OrderRepository repo;
    // Tests run against H2 in-memory by default
}
```

**Rules:**

- Unit test services with Mockito — no Spring context needed.
- Use `@WebMvcTest` for controller tests (fast, loads only web layer).
- Use `@DataJpaTest` for repository tests (loads only JPA layer).
- Reserve `@SpringBootTest` for true integration/e2e tests (slow — use sparingly).
- Aim for: many unit tests, some slice tests, few full integration tests.
- Test behavior, not implementation details.

---

## 11. Logging

```java

@Slf4j                              // Lombok — injects 'log' field via SLF4J
@Service
public class OrderService {

    public OrderResponse findById(Long id) {
        log.debug("Fetching order id={}", id);       // structured params, not string concat
        // ...
        log.info("Order fetched id={} status={}", order.getId(), order.getStatus());
        return toResponse(order);
    }
}
```

```yaml
logging:
  level:
    root: WARN
    com.company.appname: INFO        # your package — DEBUG in dev
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
```

**Rules:**

- Use SLF4J (`@Slf4j`) — never `System.out.println`.
- Parameterized log messages — never string concatenation (performance + injection).
- Log at `DEBUG` in dev, `INFO/WARN` in production.
- Never log passwords, tokens, credit card numbers, or PII.
- Include a correlation/request ID for traceability (use MDC).

---

## 12. Common Pitfalls to Avoid

| Pitfall                                        | Fix                                           |
|------------------------------------------------|-----------------------------------------------|
| `@Transactional` method called from same class | Move to separate bean or use `self` injection |
| Returning JPA entity from controller           | Map to DTO before returning                   |
| `open-in-view=true` (default)                  | Set to `false` in `application.yml`           |
| N+1 query in loop                              | Use `JOIN FETCH` or `@EntityGraph`            |
| `@Data` on entity                              | Use `@Getter @Setter` explicitly              |
| `EnumType.ORDINAL`                             | Always use `EnumType.STRING`                  |
| Hardcoded secrets in source                    | Use env vars or secrets manager               |
| Field injection `@Autowired`                   | Constructor injection only                    |
| `ddl-auto: update` in prod                     | Use Flyway or Liquibase                       |
| Catching generic `Exception` silently          | Log it, rethrow or map to specific error      |

---

## 13. Quick Dependency Reference (Spring Boot 3.x)

```xml
<!-- Essential starters -->
        spring-boot-starter-web
        spring-boot-starter-data-jpa
        spring-boot-starter-validation
        spring-boot-starter-security
        spring-boot-starter-test          <!-- includes JUnit 5, Mockito, AssertJ -->

        <!-- Common additions -->
        org.projectlombok:lombok
        org.mapstruct:mapstruct
        org.flywaydb:flyway-core
        com.h2database:h2 (test scope)
        io.jsonwebtoken:jjwt-api
```

---

## Summary Checklist

- [ ] Constructor injection, `final` fields, `@RequiredArgsConstructor`
- [ ] Controllers delegate to services; services use repositories
- [ ] DTOs in/out — never expose entities
- [ ] `@Transactional(readOnly = true)` on service class, override for writes
- [ ] `@RestControllerAdvice` global exception handler
- [ ] `open-in-view=false`, `ddl-auto=validate`, secrets from env
- [ ] BCrypt passwords, stateless JWT, HTTPS
- [ ] Unit tests (Mockito) + slice tests (`@WebMvcTest`, `@DataJpaTest`)
- [ ] SLF4J logging, parameterized, no PII
- [ ] Flyway/Liquibase for DB migrations
