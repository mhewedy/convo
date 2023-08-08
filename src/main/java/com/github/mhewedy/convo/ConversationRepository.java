package com.github.mhewedy.convo;

import brave.baggage.BaggageField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisKeyValueTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ConversationRepository {

    private final Tracer tracer;
    private final Nullifier nullifier;
    private final RedisKeyValueTemplate template;

    public ConversationRepository(Tracer tracer, ObjectMapper objectMapper, RedisKeyValueTemplate template) {
        this.tracer = tracer;
        this.template = template;
        this.nullifier = new Nullifier(objectMapper, template);
    }

    /**
     * Used only with public APIs, where there's no user in the context used as ownerId.
     * <p>
     *
     * @param ownerId and object that owns the conversation object.
     */
    public <T extends AbstractConversationHolder> void update(Object ownerId, T t) {
        if (t == null) {
            throw new ConversationException("object_is_null");
        }

        setIdIfNull(t);
        t.ownerId = ownerId;
        nullifier.nullifyNextStepsFields(t);
        template.update(t);
    }

    /**
     * Used only with public APIs, where there's no user in the context used as ownerId
     * <p>
     *
     * @param ownerId and object that owns the conversation object.
     * @throws ConversationException in case no conversation found by the provided id
     */
    public <T extends AbstractConversationHolder> T findById(Object ownerId, String id, Class<T> clazz) {
        return template.findById(id, clazz)
                .filter(it -> ownerId.equals(it.ownerId))
                .orElseThrow(() -> new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId))
                );
    }

    /**
     * Used only with public APIs, where there's no user in the context used as ownerId
     * <p>
     *
     * @param ownerId and object that owns the conversation object.
     */
    public <T extends AbstractConversationHolder> void remove(Object ownerId, String id, Class<T> clazz) {
        var objectToRemove = template.findById(id, clazz);
        objectToRemove.ifPresent(it -> {
            if (!ownerId.equals(it.ownerId)) {
                throw new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId));
            }
            template.delete(it);
        });
    }

    private <T extends AbstractConversationHolder> void setIdIfNull(T t) {
        if (t.id == null) {
            var baggageField = BaggageField.getByName(Constants.CONVERSATION_TRACE_FIELD);
            if (baggageField != null && baggageField.getValue() != null) {
                t.id = baggageField.getValue();
            } else {
                t.id = TraceUtil.getId(tracer);
            }
            log.debug("setting conversation id with value: {}", t.id);
        }
    }

    /**
     * Base class for all conversation holder.
     * All subclasses should be cached in redis with TTL
     */
    public static abstract class AbstractConversationHolder {
        @Id
        public String id;
        @JsonIgnore
        public Object ownerId;
    }
}
