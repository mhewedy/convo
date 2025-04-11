# Convo

**Convo** is a lightweight Conversation Repository that enables managing state across multistep API workflows.
It enforces step-by-step progress and guards against tampering or jumping between API steps.

## Installation

For Spring Boot 3.x:

```xml
<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.1.3</version>
</dependency>
```

For Spring Boot 2.x:

```xml
<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.0.10</version>
</dependency>
```

## Configuration

To start using Convo, define an `IdGenerator` bean:

```java
@Bean
public IdGenerator idGenerator() {
    return () -> UUID.randomUUID().toString(); // e.g., trace ID, correlation ID, etc.
}
```

### Backend Selection
Convo selects the storage backend automatically:

- Uses **Redis** if `RedisTemplate` is on the classpath.
- Falls back to **JDBC** if `JdbcTemplate` is present.
- If neither is available, a custom `StoreRepository` must be provided.

You can force a backend explicitly via:
```
convo.store=redis|jdbc
```

## Usage

Create a class that defines the conversation state. You can annotate it for TTL and versioning:

> SQL schema examples are available in [resources/sql](src/main/resources/sql). Supports any JDBC-compliant database.

```java
//@TimeToLive(duration = "PT30M") // Optional: expire conversation after inactivity
//@Version("1") // Optional: for backward compatibility with schema changes
public static class RegistrationConversation extends AbstractConversationHolder {

    @Step(1)
    public String mobileNumber;

    @Step(2)
    public VerifiedUserData verifiedUserData;

    public static class VerifiedUserData {
        public String name;
    }
}
```

### Controller Example

```java
@RestController
public class RegistrationController {

    private final ConversationRepository conversationRepository;

    public RegistrationController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    // Step 1: Submit and validate mobile number
    @PostMapping("/api/register/verify-mobile-number")
    public ResponseEntity<Void> verifyMobileNumber(@RequestParam String mobileNumber) {
        var conv = new RegistrationConversation();
        // Validate and store the mobile number
        conv.mobileNumber = mobileNumber;
        conversationRepository.update(null, conv);
        return ResponseEntity.ok().header(Constants.X_CONVERSATION_ID, conv.id).build();
    }

    // Step 2: Add verified user details
    @PostMapping("/api/register/verify-user-data")
    public ResponseEntity<Void> verifyUserData(@RequestHeader(Constants.X_CONVERSATION_ID) String conversationId) {
        var conv = conversationRepository.findById(null, conversationId, RegistrationConversation.class);
        conv.verifiedUserData = new RegistrationConversation.VerifiedUserData();
        //conv.verifiedUserData.name = ... get name from some service
        conversationRepository.update(null, conv);
        return ResponseEntity.ok().header(Constants.X_CONVERSATION_ID, conversationId).build();
    }

    // Step 3: Complete registration
    @PostMapping("/api/register/register-user")
    public ResponseEntity<Void> register(@RequestHeader(Constants.X_CONVERSATION_ID) String conversationId) {
        var conv = conversationRepository.findById(null, conversationId, RegistrationConversation.class);
        // Save the data and create a user in your system
        conversationRepository.remove(conversationId);
        return ResponseEntity.ok().build();
    }
}
```

## Demo

Explore the demo project here:
[https://github.com/mhewedy/convo-demo](https://github.com/mhewedy/convo-demo)

