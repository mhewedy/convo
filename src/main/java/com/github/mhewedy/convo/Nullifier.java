package com.github.mhewedy.convo;

import tools.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.annotations.Step;
import com.github.mhewedy.convo.store.StoreRepository;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.mhewedy.convo.FieldIterator.onEachField;

@Slf4j
class Nullifier {

    private final ObjectMapper objectMapper;
    private final StoreRepository storeRepository;

    public Nullifier(ObjectMapper objectMapper, StoreRepository storeRepository) {
        this.objectMapper = objectMapper;
        this.storeRepository = storeRepository;
    }

    <T extends AbstractConversationHolder> void nullifyNextStepsFields(T fromUser) {

        validateSteps(fromUser);
        final List<Field> updatedFields = getUpdatedFields(fromUser);

        List<Integer> updatedSteps = updatedFields.stream()
                .map(it -> it.getAnnotation(Step.class))
                .map(Step::value)
                .distinct().toList();

        if (updatedSteps.size() > 1) {
            throw new RuntimeException("you can not update more than one step at atime. " + fromUser +
                    ", you try to update fields: " + updatedFields);
        }
        if (!updatedSteps.isEmpty())
            nullifyNextSteps(fromUser, updatedSteps.get(0));
    }

    private <T extends AbstractConversationHolder> void validateSteps(T fromUser) {

        var steps = onEachField(fromUser, field -> {
            if (field.getAnnotation(Step.class) == null) {
                throw new RuntimeException("all conversation fields should have @Step annotation: " +
                        fromUser + ", field: " + field);
            }
            return field.getAnnotation(Step.class).value();
        });
        if (steps.isEmpty()) return;

        List<Integer> sortedUniqueSteps = steps.stream().sorted().distinct().toList();

        if (sortedUniqueSteps.get(0) < 1) {
            throw new RuntimeException("steps should start with 1");
        }
        if (sortedUniqueSteps.size() < steps.size()) {
            throw new RuntimeException("no duplicate steps allowed");
        }
        Integer latestStep = sortedUniqueSteps.get(sortedUniqueSteps.size() - 1);
        if (latestStep != sortedUniqueSteps.size()) {
            throw new RuntimeException("no gaps allowed in step numbers");
        }
    }

    private <T extends AbstractConversationHolder> List<Field> getUpdatedFields(T fromUser) {
        var fromRedis = storeRepository.findById(fromUser.id, fromUser.getClass()).orElse(null);

        return onEachField(fromUser, field -> {

            var valueFromUser = field.get(fromUser);
            if (fromRedis == null) {
                if (valueFromUser != null) {
                    return field;
                }
            } else {
                if (valueFromUser != null && !asJson(valueFromUser).equals(asJson(field.get(fromRedis)))) {
                    return field;
                }
            }
            return null;
        });
    }

    private <T extends AbstractConversationHolder> void nullifyNextSteps(T fromUser, int currentStep) {
        onEachField(fromUser, field -> {
            int step = field.getAnnotation(Step.class).value();
            if (step > currentStep && field.get(fromUser) != null) {
                log.warn("setting null for: {} in object: {}", field.getName(), fromUser);
                field.set(fromUser, null);
            }
        });
    }

    private <T> String asJson(T t) {
        if (t == null) return "";
        return objectMapper.writeValueAsString(t);
    }
}
