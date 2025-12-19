package com.github.mhewedy.convo;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
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
     * Saves (Create or Update) the conversation object to the store.
     * The conversation id can be obtained by calling {@link AbstractConversationHolder#id}.
     *
     * @param ownerId is the object that owns the conversation object, usually the current user id (can be null)
     */
    public <T extends AbstractConversationHolder> void save(@Nullable Object ownerId, T t) {
        if (t == null) {
            throw new ConversationException("object_is_null");
        }

        setVersionIfNew(t);
        setIdIfNull(t);
        t._ownerId = normalize(ownerId);
        nullifier.nullifyNextStepsFields(t);
        storeRepository.save(t);
    }

    /**
     * Return the conversation object from the store.
     *
     * @param ownerId is the object that owns the conversation object, usually the current user id (can be null)
     * @throws ConversationException in case no conversation found by the provided id
     */
    public <T extends AbstractConversationHolder> T findById(@Nullable Object ownerId, String id, Class<T> clazz) {
        T object = storeRepository.findById(id, clazz)
                .filter(it -> ownerId == null || normalize(ownerId).equals(it._ownerId))
                .orElseThrow(() -> new ConversationException("conversation with specified id does not exist for the given owner",
                        Map.of("conversationId", id, "ownerId", ownerId + ""))
                );
        validateVersionIfRequired(object);
        return object;
    }

    /**
     * Removes the conversation object from the store.
     *
     * @param ownerId is the object that owns the conversation object, usually the current user id
     */
    public <T extends AbstractConversationHolder> void delete(@Nullable Object ownerId, String id, Class<T> clazz) {
        var objectToRemove = storeRepository.findById(id, clazz);
        objectToRemove.ifPresent(it -> {
            if (ownerId != null && !normalize(ownerId).equals(it._ownerId)) {
                throw new ConversationException("conversation with specified id does not exist for the given owner",
                        Map.of("conversationId", id, "ownerId", ownerId));
            }
            storeRepository.delete(it);
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

    /**
     * Sets the conversation ID for the given conversation holder object if the ID is currently null.
     * <br/>
     * If a conversation ID is already set in the current request context (coming from the http request header
     * {@link Constants#X_CONVERSATION_ID}), it uses that ID.
     * Otherwise, it generates a new ID, sets it in the request context, and assigns it to the object.
     */
    private <T extends AbstractConversationHolder> void setIdIfNull(T t) {
        if (t.id == null) {
            var currentId = ConversationFilter.getCurrentConversationId();
            if (currentId != null) {
                t.id = currentId;
            } else {
                var newId = idGenerator.generateNewConversationId();
                ConversationFilter.setCurrentConversationId(newId);
                t.id = newId;
            }
            log.debug("setting conversation id with value: {}, type: {}", t.id, t.getClass().getSimpleName());
        }
    }

    private String normalize(@Nullable Object id) {
        return id == null ? null : id.toString();
    }
}
