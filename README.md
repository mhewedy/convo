# Convo

The Conversation Repository

## Install

```xml

<dependency>
    <groupId>com.github.mhewedy</groupId>
    <artifactId>convo</artifactId>
    <version>0.0.7</version>
</dependency>
```

## Setup:

Create bean of type `IdGenerator` e.g.:

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
TODO

## Demo
See https://github.com/mhewedy/convo-demo
