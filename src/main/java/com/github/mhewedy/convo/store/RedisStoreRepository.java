package com.github.mhewedy.convo.store;

import com.github.mhewedy.convo.AbstractConversationHolder;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

// Not tested yet!
public class RedisStoreRepository implements StoreRepository {

    private final RedisTemplate<String, AbstractConversationHolder> redisTemplate;

    public RedisStoreRepository(RedisTemplate<String, AbstractConversationHolder> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public <T extends AbstractConversationHolder> void update(T t) {
        redisTemplate.opsForValue().set(t.id, t, Util.getTimeToLive(t));
    }

    @Override
    public <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz) {
        AbstractConversationHolder holder = redisTemplate.opsForValue().get(id);
        return Optional.ofNullable(clazz.cast(holder));
    }

    @Override
    public <T extends AbstractConversationHolder> void delete(T it) {
        redisTemplate.delete(it.id);
    }
}
