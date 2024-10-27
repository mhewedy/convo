package com.github.mhewedy.convo.redis;

import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.StoreRepository;

import java.util.Optional;

// TODO
public class RedisStoreRepository implements StoreRepository {

    @Override
    public <T extends AbstractConversationHolder> void update(T t) {

    }

    @Override
    public <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz) {
        return Optional.empty();
    }

    @Override
    public <T extends AbstractConversationHolder> void delete(T it) {

    }
}
