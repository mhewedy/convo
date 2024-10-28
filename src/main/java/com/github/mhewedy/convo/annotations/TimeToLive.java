package com.github.mhewedy.convo.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface TimeToLive {
    String duration() default "PT30M";
}
