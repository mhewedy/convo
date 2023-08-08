package com.github.mhewedy.convo;

import java.util.Collections;
import java.util.Map;

public class ConversationException extends RuntimeException {

    public Map<String, Object> args;

    /**
     * Try to use other versions of the exception to tag your exception with related info
     *
     * @see #ConversationException(String, String, Object)
     * @see #ConversationException(String, Map)
     */
    public ConversationException(String message) {
        this(message, Collections.emptyMap());
    }

    /**
     * Same as
     * <pre>
     * throw new ConversationException(message, Map.of(key, value))
     * </pre>
     */
    public ConversationException(String message, String key, Object value) {
        this(message, Map.of(key, value));
    }


    /**
     * Usage:
     * <pre>
     * throw new ConversationException("some_message_key",
     *      Map.of("personId", 1234567890, "birthDate", birthDate))
     * </pre>
     */
    public ConversationException(String message, Map<String, Object> args) {
        super(message);
        this.args = args;
    }

}
