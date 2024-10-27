package com.github.mhewedy.convo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

@Slf4j
@Component
public class ConversationRepository {

    private final IdManager idManager;
    private final Nullifier nullifier;
    private final StoreRepository storeRepository;

    public ConversationRepository(IdManager idManager, ObjectMapper objectMapper, StoreRepository storeRepository) {
        this.idManager = idManager;
        this.storeRepository = storeRepository;
        this.nullifier = new Nullifier(objectMapper, storeRepository);
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

        setVersionIfNew(t);
        setIdIfNull(t);
        t.ownerId = ownerId;
        nullifier.nullifyNextStepsFields(t);
        storeRepository.update(t);
    }

    /**
     * Used only with public APIs, where there's no user in the context used as ownerId
     * <p>
     *
     * @param ownerId and object that owns the conversation object.
     * @throws ConversationException in case no conversation found by the provided id
     */
    public <T extends AbstractConversationHolder> T findById(Object ownerId, String id, Class<T> clazz) {
        T object = storeRepository.findById(id, clazz)
                .filter(it -> ownerId.equals(it.ownerId))
                .orElseThrow(() -> new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId))
                );
        validateVersionIfRequired(object);
        return object;
    }

    /**
     * Used only with public APIs, where there's no user in the context used as ownerId
     * <p>
     *
     * @param ownerId and object that owns the conversation object.
     */
    public <T extends AbstractConversationHolder> void remove(Object ownerId, String id, Class<T> clazz) {
        var objectToRemove = storeRepository.findById(id, clazz);
        objectToRemove.ifPresent(it -> {
            if (!ownerId.equals(it.ownerId)) {
                throw new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId));
            }
            storeRepository.delete(it);
        });
    }

    private <T extends AbstractConversationHolder> void setVersionIfNew(T t) {
        if (t.id == null && t.getClass().isAnnotationPresent(Version.class)) {
            t.version = t.getClass().getAnnotation(Version.class).value();
            log.debug("creating conversation of type {} with version: {}", t.getClass().getSimpleName(), t.version);
        }
    }

    private <T extends AbstractConversationHolder> void validateVersionIfRequired(T t) {
        if (t.getClass().isAnnotationPresent(Version.class)) {
            String currentVersion = t.getClass().getAnnotation(Version.class).value();
            if (!currentVersion.equalsIgnoreCase(t.version)) {
                throw new ConversationException("invalid_conversation_version", "conversationId", t.id);
            }
        }
    }

    private <T extends AbstractConversationHolder> void setIdIfNull(T t) {
        if (t.id == null) {

            var attrs = RequestContextHolder.getRequestAttributes();
            String idFromRequest = attrs == null ? null :
                    (String) attrs.getAttribute(Constants.CONVERSATION_FIELD, RequestAttributes.SCOPE_REQUEST);

            if (idFromRequest != null) {
                t.id = idFromRequest;
            } else {
                t.id = idManager.getConversationId();
            }
            log.debug("setting conversation id with value: {}, type: {}", t.id, t.getClass().getSimpleName());
        }
    }

    /**
     * Base class for all conversation holder.
     * All subclasses should be cached in redis with TTL
     */
    public static abstract class AbstractConversationHolder {
        public String id;
        @JsonIgnore
        public Object ownerId;
        public String version;
    }
}
