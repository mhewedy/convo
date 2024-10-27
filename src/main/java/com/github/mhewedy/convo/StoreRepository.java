package com.github.mhewedy.convo;

import java.util.Optional;

public interface StoreRepository {

    <T extends AbstractConversationHolder> void update(T t);

    <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz);

    <T extends AbstractConversationHolder> void delete(T it);
}
