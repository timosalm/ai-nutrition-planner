package com.example.nutritionplanner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.hitl.FormBindingRequest;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.ux.form.Button;
import com.embabel.ux.form.Form;
import com.embabel.ux.form.TextArea;
import com.embabel.ux.form.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.thymeleaf.TemplateEngine;

import static org.apache.commons.lang3.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController extends SseInteractionController {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerUiController.class);
    private final AgentPlatform agentPlatform;
    private final ModelProvider modelProvider;
    private final ConcurrentHashMap<String, HitlHandler> hitlHandlers = new ConcurrentHashMap<>();

    NutritionPlannerUiController(AgentPlatform agentPlatform, ModelProvider modelProvider, TemplateEngine templateEngine) {
        super(templateEngine);
        this.agentPlatform = agentPlatform;
        this.modelProvider = modelProvider;
    }

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("aiModel", getAiModelName());
        return "login";
    }

    @GetMapping("/")
    String form(Model model) {
        model.addAttribute("aiModel", getAiModelName());
        model.addAttribute("weeklyPlanRequest", new WeeklyPlanRequest());
        return "index";
    }

    @PostMapping("/plan")
    String createPlan(@ModelAttribute WeeklyPlanRequest request, Principal principal, Model model) {
        var username = principal.getName();

        return eventStream(model, interactionId -> {
            var hitlHandler = new HitlHandler(form ->
                    sendEvent(interactionId, "fragments/hitl", Map.of("title", form.getTitle(), "fields", formFields(form))));
            hitlHandlers.put(interactionId, hitlHandler);
            AgentProcess process = null;
            try {
                process = AgentInvocation.create(agentPlatform, WeeklyPlan.class)
                        .run(Map.of("user", username, "request", request));

                // The process pauses with WAITING whenever an action calls WaitFor.formSubmission(...)
                while (process.getStatus() == AgentProcessStatusCode.WAITING) {
                    hitlHandler.handle((FormBindingRequest<?>) process.lastResult(), process);
                    process.run();
                }

                var plan = process.resultOfType(WeeklyPlan.class);
                sendEvent(interactionId, "fragments/plan", Map.of("plan", plan));
                completeInteraction(interactionId);
            } catch (RuntimeException e) {
                if (process != null && !process.getFinished()) process.kill();
                throw e;
            } finally {
                hitlHandlers.remove(interactionId);
            }
        });
    }

    @PostMapping("/interaction/{interactionId}/answers")
    @ResponseBody
    String provideAnswers(@PathVariable String interactionId, @RequestParam Map<String, String> answers) {
        var handler = hitlHandlers.get(interactionId);
        if (handler != null) handler.provideAnswers(answers);
        return "";
    }

    private String getAiModelName() {
        try {
            var defaultLlm = FieldUtils.readField(modelProvider, "defaultLlm", true);
            var name = (String) FieldUtils.readField(defaultLlm, "name", true);
            var provider = (String) FieldUtils.readField(defaultLlm, "provider", true);
            return "%s (%s)".formatted(provider, capitalize(name));
        } catch (Exception e) {
            return "";
        }
    }

    private static List<FormField> formFields(Form form) {
        return form.getControls().stream()
                .filter(control -> !(control instanceof Button))
                .map(control -> switch (control) {
                    case TextField field -> new FormField(field.getId(), field.getLabel(), field.getPlaceholder());
                    case TextArea field -> new FormField(field.getId(), field.getLabel(), field.getPlaceholder());
                    default -> throw new IllegalStateException("Unsupported form control: " + control.getType());
                })
                .toList();
    }

    public record FormField(String id, String label, String placeholder) {}
}
