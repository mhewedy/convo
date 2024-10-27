package com.github.mhewedy.convo.store;

import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.annotations.TimeToLive;

import java.time.Duration;

class Util {

    public static <T extends AbstractConversationHolder> Duration getTimeToLive(T t) {
        String ttlStr;
        try {
            ttlStr = (String) TimeToLive.class.getMethod("duration").getDefaultValue();
            if (t.getClass().isAnnotationPresent(TimeToLive.class)) {
                ttlStr = t.getClass().getAnnotation(TimeToLive.class).duration();
            }
            return Duration.parse(ttlStr);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
