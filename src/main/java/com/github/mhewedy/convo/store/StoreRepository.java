package com.github.mhewedy.convo.store;

import com.github.mhewedy.convo.AbstractConversationHolder;

import java.util.Optional;

public interface StoreRepository {

    <T extends AbstractConversationHolder> void update(T t);

    <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz);

    <T extends AbstractConversationHolder> void remove(T it);
}
