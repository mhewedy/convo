package com.github.mhewedy.convo.store;

import com.github.mhewedy.convo.AbstractConversationHolder;

import java.util.Optional;

// TODO
public class RedisStoreRepository implements StoreRepository {

    @Override
    public <T extends AbstractConversationHolder> void update(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends AbstractConversationHolder> void delete(T it) {
        throw new UnsupportedOperationException();
    }
}
