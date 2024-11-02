package com.github.mhewedy.convo.store;

import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.annotations.TimeToLive;

import java.time.Duration;

class Util {

    public static <T extends AbstractConversationHolder> Duration getTimeToLive(T t) {
        String ttlStr;
        try {
            if (t.getClass().isAnnotationPresent(TimeToLive.class)) {
                ttlStr = t.getClass().getAnnotation(TimeToLive.class).duration();
            } else {
                ttlStr = (String) TimeToLive.class.getMethod("duration").getDefaultValue();
            }
            return Duration.parse(ttlStr);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
