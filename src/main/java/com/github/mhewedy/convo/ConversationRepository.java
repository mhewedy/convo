package com.github.mhewedy.convo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.annotations.Version;
import com.github.mhewedy.convo.store.StoreRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ConversationRepository {

    private final IdGenerator idGenerator;
    private final Nullifier nullifier;
    private final StoreRepository storeRepository;

    public ConversationRepository(IdGenerator idGenerator, ObjectMapper objectMapper, StoreRepository storeRepository) {
        this.idGenerator = idGenerator;
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
                .filter(it -> ownerId == null || ownerId.equals(it.ownerId))
                .orElseThrow(() -> new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId + ""))
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
            if (ownerId != null && !ownerId.equals(it.ownerId)) {
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
            t.id = getConversationId(idGenerator);
            log.debug("setting conversation id with value: {}, type: {}", t.id, t.getClass().getSimpleName());
        }
    }

    private String getConversationId(IdGenerator idGenerator) {
        var idFromRequest = IdGenerator.getConversationIdFromRequest();
        if (idFromRequest != null) {
            return idFromRequest;
        } else {
            return idGenerator.generateNewConversationId();
        }
    }
}
