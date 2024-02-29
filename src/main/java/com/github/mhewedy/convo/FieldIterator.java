package com.github.mhewedy.convo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.github.mhewedy.convo.ConversationRepository.AbstractConversationHolder;
import static org.springframework.util.ReflectionUtils.doWithFields;

class FieldIterator {

    static <T extends AbstractConversationHolder> void onEachField(T fromUser, Function0<Field> fn) {
        onEachField(fromUser, (Function1<Field, Void>) field -> {
            fn.apply(field);
            return null;
        });
    }

    static <T extends AbstractConversationHolder, U> List<U> onEachField(T fromUser, Function1<Field, U> fn) {
        List<U> ret = new ArrayList<>();
        doWithFields(fromUser.getClass(), field -> {
            try {
                field.setAccessible(true);
                U r = fn.apply(field);
                if (r != null) {
                    ret.add(r);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, field -> !List.of("ownerId", "id", "version").contains(field.getName()));
        return ret;
    }

    interface Function0<T> {
        void apply(T t) throws Exception;
    }

    interface Function1<T, R> {
        R apply(T t) throws Exception;
    }
}
