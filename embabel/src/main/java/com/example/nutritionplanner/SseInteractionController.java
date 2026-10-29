package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

abstract class SseInteractionController {

    private static final Logger log = LoggerFactory.getLogger(SseInteractionController.class);

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final TemplateEngine templateEngine;

    SseInteractionController(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @GetMapping(path = "/interactions/{interactionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter interactionEvents(@PathVariable String interactionId) {
        return emitters.get(interactionId);
    }

    String eventStream(Model model, Consumer<String> publisher) {
        var interactionId = UUID.randomUUID().toString();
        createEmitter(interactionId);

        CompletableFuture.runAsync(() -> publisher.accept(interactionId))
                .exceptionally(throwable -> {
                    failInteraction(interactionId, throwable);
                    return null;
                });

        model.addAttribute("interactionId", interactionId);
        return "fragments/events :: events";
    }

    private void createEmitter(String interactionId) {
        var emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(interactionId, emitter);
        emitter.onCompletion(() -> emitters.remove(interactionId));
        emitter.onTimeout(() -> emitters.remove(interactionId));
    }

    protected void sendEvent(String interactionId, String template, Map<String, Object> data) {
        var emitter = emitters.get(interactionId);
        if (emitter == null) return;

        var context = new Context();
        context.setVariables(data);
        context.setVariable("interactionId", interactionId);
        var content = templateEngine.process(template, context);
        try {
            emitter.send(SseEmitter.event().name("content-update").data(content));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * Reports a failure of the asynchronous work behind an interaction to the browser and closes the
     * event stream. Without this the exception would be swallowed by the {@link CompletableFuture} and
     * the stream would stay open forever, leaving the UI waiting for a response that never arrives.
     */
    private void failInteraction(String interactionId, Throwable throwable) {
        var cause = (throwable instanceof CompletionException && throwable.getCause() != null)
                ? throwable.getCause() : throwable;
        log.error("Interaction {} failed", interactionId, cause);
        sendEvent(interactionId, "fragments/error", Map.of("message", errorMessage(cause)));
        completeInteraction(interactionId);
    }

    private String errorMessage(Throwable cause) {
        return cause.getMessage() != null ? "%s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage())
                : cause.getClass().getSimpleName();
    }

    protected void completeInteraction(String interactionId) {
        var emitter = emitters.get(interactionId);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
