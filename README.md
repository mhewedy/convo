# Convo

**Convo** is a lightweight Conversation Repository for Spring Boot applications that enables managing state across multistep API workflows. It enforces step-by-step progress and guards against tampering or skipping steps in your API flows, making it ideal for complex processes like user registration, checkout flows, and multi-stage form submissions.

## Features

- **Step-by-Step Progression**: Enforce sequential progression through API steps
- **State Management**: Maintain conversation state between API calls
- **Security**: Prevent tampering with conversation data or skipping required steps
- **Multiple Storage Options**: Support for Redis and JDBC backends
- **Automatic Cleanup**: Time-to-live (TTL) functionality for expired conversations
- **Versioning**: Detect compatibility based on the version, which reduce issues due to conversation changes
- **Owner Association**: Bind conversations to specific users for added security
- **Spring Boot Integration**: Seamless integration with Spring Boot applications

## Installation

For Spring Boot 4.x:

```xml
<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.2.1</version>
</dependency>
```

For Spring Boot 3.x:

```xml
<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.1.6</version>
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

### Required Configuration

To start using Convo, define an `IdGenerator` bean:

```java
@Bean
public IdGenerator idGenerator() {
    return () -> UUID.randomUUID().toString(); // e.g., trace ID, correlation ID, etc.
}
```

### Storage Backend Selection

Convo selects the storage backend automatically based on available dependencies:

- Uses **Redis** if `RedisTemplate` is on the classpath (default)
- Falls back to **JDBC** if `JdbcTemplate` is present
- If neither is available, a custom `StoreRepository` must be provided

You can force a specific storage backend via application properties:
```properties
convo.store=redis|jdbc|custom
```

### JDBC Configuration

When using JDBC as the backend, you need to create the required database table. SQL schema examples are available in [resources/sql](src/main/resources/sql) for:
- PostgreSQL
- MySQL
- Microsoft SQL Server

Additional JDBC configuration options:
```properties
# Enable/disable automatic cleanup of expired conversations (default: true)
convo.jdbc.cleanup.enabled=true

# Set the interval for cleanup operations (default: 15 minutes)
convo.jdbc.cleanup.interval=PT15M
```

## Core Concepts

### Conversation Holder

A conversation holder is a class that extends `AbstractConversationHolder` and defines the structure of your conversation state. Each field that represents a step in your workflow should be annotated with `@Step` and a step number.

### Annotations

- **@Step**: Marks a field as a step in the conversation with a specific order
- **@TimeToLive**: Sets the expiration time for a conversation (default: 30 minutes)
- **@Version**: Provides versioning for backward compatibility when conversation schemas change

## Usage

### Creating a Conversation Holder

Create a class that defines the conversation state:

```java
@TimeToLive(duration = "PT30M") // Optional: expire conversation after inactivity (default 30 minutes)
@Version("1") // Optional: for backward compatibility with schema changes
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

### Using the Conversation Repository

The `ConversationRepository` provides methods to create, retrieve, update, and delete conversations:

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
        conversationRepository.remove(conversationId, RegistrationConversation.class);
        return ResponseEntity.ok().build();
    }
}
```

### Owner Association

You can associate conversations with a specific owner (typically a user ID) for added security. The ConversationRepository methods accept an owner ID parameter:

- **update(ownerId, conversation)**: Creates or updates a conversation associated with the specified owner
- **findById(ownerId, conversationId, conversationClass)**: Retrieves a conversation, ensuring it belongs to the specified owner
- **remove(ownerId, conversationId, conversationClass)**: Removes a conversation, ensuring it belongs to the specified owner

The owner ID can be a String, Long, or any object that can be converted to a string. When a conversation is retrieved or removed, Convo verifies that the specified owner ID matches the conversation's owner.

## Advanced Features

### Automatic Field Nullification

Convo automatically nullifies fields for steps that haven't been reached yet. This prevents clients from submitting data for future steps before completing the current step.

### Conversation Expiration

Conversations automatically expire after the time-to-live period specified with the `@TimeToLive` annotation. This helps clean up stale conversations and prevents resource leaks.

### Version Compatibility

The `@Version` annotation helps maintain backward compatibility when conversation schemas change. If a conversation's version doesn't match the current class version, an exception is thrown.

## Demo

Explore the demo project here:
[https://github.com/mhewedy/convo-demo](https://github.com/mhewedy/convo-demo)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the terms of the license included in the repository.
