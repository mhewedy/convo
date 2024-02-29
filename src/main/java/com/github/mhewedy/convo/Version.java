package com.github.mhewedy.convo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This annotation is optional. used to mark a version for a conversation to avoid deserialization issues.
 * <p>
 * increase the version with each major non-backward compatible change to the conversation object
 * to avoid deserialization issues with current data in redis.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface Version {
    String value();
}
