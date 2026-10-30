package com.example.nutritionplanner;

import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.hitl.FormBindingRequest;
import com.embabel.agent.core.hitl.FormResponse;
import com.embabel.ux.form.Form;
import com.embabel.ux.form.FormSubmission;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Answers a form request created by Embabel's {@code WaitFor.formSubmission(...)}: hands the generated
 * form to the form handler, blocks until the user submits answers, and binds them onto the blackboard
 * so the GOAP planner can resume.
 */
class HitlHandler {

    private final AtomicReference<CompletableFuture<Map<String, String>>> pendingAnswers = new AtomicReference<>();
    private final Consumer<Form> formHandler;

    HitlHandler(Consumer<Form> formHandler) {
        this.formHandler = formHandler;
    }

    void handle(FormBindingRequest<?> formRequest, AgentProcess process) {
        var form = formRequest.getPayload();
        var submission = new FormSubmission(form.getId(), awaitAnswers(form), UUID.randomUUID().toString(), Instant.now());
        var response = new FormResponse(UUID.randomUUID().toString(), formRequest.getId(), submission, false, Instant.now());
        formRequest.onResponse(response, process);
    }

    private Map<String, String> awaitAnswers(Form form) {
        var future = new CompletableFuture<Map<String, String>>();
        pendingAnswers.set(future);
        formHandler.accept(form);
        try {
            return Map.copyOf(future.get(5, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout waiting for user response", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void provideAnswers(Map<String, String> answers) {
        var future = pendingAnswers.getAndSet(null);
        if (future != null) future.complete(answers);
    }
}
