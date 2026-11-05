package com.example.nutritionplanner;

import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.springframework.util.ClassUtils;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class HitlHandler {

    private final AtomicReference<CompletableFuture<Map<String, String>>> pendingAnswers = new AtomicReference<>();
    private final Consumer<Class<? extends Record>> formHandler;

    HitlHandler(Consumer<Class<? extends Record>> formHandler) {
        this.formHandler = formHandler;
    }

    <T> ResultWithAgenticScope<T> handle(ResultWithAgenticScope<T> suspendedResult) {
        var responseId = suspendedResult.agenticScope().pendingResponseIds().iterator().next();
        var responseType = ClassUtils.resolveClassName(responseId, null).asSubclass(Record.class);
        return suspendedResult.completePendingResponse(responseId, toRecord(responseType, awaitAnswers(responseType)));
    }

    private Map<String, String> awaitAnswers(Class<? extends Record> responseType) {
        var future = new CompletableFuture<Map<String, String>>();
        pendingAnswers.set(future);
        formHandler.accept(responseType);
        try {
            return Map.copyOf(future.get(5, TimeUnit.MINUTES));
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout waiting for user response", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Record toRecord(Class<? extends Record> type, Map<String, String> answers) {
        var components = type.getRecordComponents();
        var parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new);
        var values = Arrays.stream(components).map(component -> answers.get(component.getName())).toArray();
        try {
            return type.getDeclaredConstructor(parameterTypes).newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind answers to " + type.getSimpleName(), e);
        }
    }

    void provideAnswers(Map<String, String> answers) {
        var future = pendingAnswers.getAndSet(null);
        if (future != null) future.complete(answers);
    }
}
