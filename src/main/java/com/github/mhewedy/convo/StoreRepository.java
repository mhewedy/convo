package com.github.mhewedy.convo;

import java.util.Optional;

public interface StoreRepository {

    <T> void update(T t);

    <T> Optional<T> findById(String id, Class<T> clazz);

    <T> void delete(T it);
}
