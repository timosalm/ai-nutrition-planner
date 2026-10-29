package com.example.nutritionplanner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.ai.model.ModelProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.thymeleaf.TemplateEngine;

import static org.apache.commons.lang3.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController extends SseInteractionController {

    private final AgentPlatform agentPlatform;
    private final ModelProvider modelProvider;

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
        return eventStream(model, interactionId -> {
            try {
                var plan = AgentInvocation.create(agentPlatform, WeeklyPlan.class)
                        .invoke(Map.of("user", principal.getName(), "request", request));
                sendEvent(interactionId, "fragments/plan", Map.of("plan", plan));
                completeInteraction(interactionId);
            } finally {
            }
        });
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
}