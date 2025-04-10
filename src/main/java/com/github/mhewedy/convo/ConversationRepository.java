package com.github.mhewedy.convo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.annotations.Version;
import com.github.mhewedy.convo.store.StoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

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
     * @param ownerId is the object that owns the conversation object, usually the current user id
     */
    public <T extends AbstractConversationHolder> void update(@Nullable Object ownerId, T t) {
        if (t == null) {
            throw new ConversationException("object_is_null");
        }

        setVersionIfNew(t);
        setIdIfNull(t);
        t._ownerId = normalize(ownerId);
        nullifier.nullifyNextStepsFields(t);
        storeRepository.update(t);
    }

    /**
     * @param ownerId is the object that owns the conversation object, usually the current user id
     * @throws ConversationException in case no conversation found by the provided id
     */
    public <T extends AbstractConversationHolder> T findById(@Nullable Object ownerId, String id, Class<T> clazz) {
        T object = storeRepository.findById(id, clazz)
                .filter(it -> ownerId == null || normalize(ownerId).equals(it._ownerId))
                .orElseThrow(() -> new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId + ""))
                );
        validateVersionIfRequired(object);
        return object;
    }

    /**
     * @param ownerId is the object that owns the conversation object, usually the current user id
     */
    public <T extends AbstractConversationHolder> void remove(@Nullable Object ownerId, String id, Class<T> clazz) {
        var objectToRemove = storeRepository.findById(id, clazz);
        objectToRemove.ifPresent(it -> {
            if (ownerId != null && !normalize(ownerId).equals(it._ownerId)) {
                throw new ConversationException("invalid_conversation_user_combination",
                        Map.of("conversationId", id, "userId", ownerId));
            }
            storeRepository.remove(it);
        });
    }

    private <T extends AbstractConversationHolder> void setVersionIfNew(T t) {
        if (t.id == null && t.getClass().isAnnotationPresent(Version.class)) {
            t._version = t.getClass().getAnnotation(Version.class).value();
            log.debug("creating conversation of type {} with version: {}", t.getClass().getSimpleName(), t._version);
        }
    }

    private <T extends AbstractConversationHolder> void validateVersionIfRequired(T t) {
        if (t.getClass().isAnnotationPresent(Version.class)) {
            String currentVersion = t.getClass().getAnnotation(Version.class).value();
            if (!currentVersion.equalsIgnoreCase(t._version)) {
                throw new ConversationException("invalid_conversation_version", "conversationId", t.id);
            }
        }
    }

    private <T extends AbstractConversationHolder> void setIdIfNull(T t) {
        if (t.id == null) {
            t.id = getConversationId();
            log.debug("setting conversation id with value: {}, type: {}", t.id, t.getClass().getSimpleName());
        }
    }

    private String getConversationId() {
        var currentId = ConversationFilter.getCurrentConversationId();
        if (currentId != null) {
            return currentId;
        } else {
            var newId = idGenerator.generateNewConversationId();
            ConversationFilter.setCurrentConversationId(newId);
            return newId;
        }
    }

    private String normalize(@Nullable Object id) {
        return id == null ? null : id.toString();
    }
}
