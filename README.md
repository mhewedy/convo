# Convo

The Conversation Repository abstracts how to hold data between multiple API calls.
It protects against malicious user behavior by skipping API calls or trying to jump between them.

## Install
For spring-boot 3.x:
```xml

<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.1.3</version>
</dependency>
```

For spring-boot 2.x:
```xml

<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.0.10</version>
</dependency>
```

## Setup:

Define a bean of type `IdGenerator` e.g.:

```java

@Bean
public IdGenerator idGenerator() {
    return () -> UUID.randomUUID().toString();   // could be the trace id or correlation id or request id, etc ...
}
```

By default, Redis implementation will be chosen if spring `RedisTemplate` is available on the classpath,
otherwise if `JdbcTemplate` is available on the class path then the Jdbc implementation will be used,
otherwise you need to provide your own implementation of class `StoreRepository`.

You always can override this default resolution using `convo.store=redis|jdbc`

## Usage

First, create an object that will hold your conversation data

>You can find SQL schema definitions for some databases in the [resources/sql](src/main/resources/sql) directory. 
> (any JDBC-compliant DB is support)

```java
//@TimeToLive(duration = "PT30M") //optional
//@Version("1") //optional, to protect from non-backward compatibility changes to the conversation object, e.g. adding new non-nullable fields
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

Then you can create/access the conversation as follows:

```java

@RestController
public class RegistrationController {

    private ConversationRepository conversationRepository;

    // first api call
    @RequestMapping("/api/register/verify-mobile-number")
    public void verifyMobileNumber(String mobileNumber) {
        var conv = new RegistrationConversation();
        // verify mobileNumber somehow
        // .....
        conv.mobileNumber = mobileNumber;
        conversationRepository.update(null, conv);
        
        return ResponseEntity.ok().header(Constants.X_CONVERSATION_ID, conv.id).build();
    }

    // second api call
    @RequestMapping("/api/register/verify-user-data")
    public String verifyUserData(@RequestHeader(Constants.X_CONVERSATION_ID) String conversationId) {
        var conv = conversationRepository.findById(null, conversationId, RegistrationConversation.class);
        conv.verifiedUserData = new RegistrationConversation.VerifiedUserData();
        conv.verifiedUserData.name = getFromSomeVerifiedPlace();
        conversationRepository.update(null, conv);
        
        return ResponseEntity.ok().header(Constants.X_CONVERSATION_ID, conversationId).build();
    }

    // third api call
    @RequestMapping("/api/register/register-user")
    public void register(@RequestHeader(Constants.X_CONVERSATION_ID) String conversationId) {
        var conv = conversationRepository.findById(null, conversationId, RegistrationConversation.class);
        // save data from conv to db to create the new user 
        conversationRepository.remove(conversationId);
    }
}
```

## Demo

See https://github.com/mhewedy/convo-demo
