package com.example.nutritionplanner;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController {

    private final ChatModel chatModel;
    private final NutritionPlannerAgent nutritionPlannerAgent;

    NutritionPlannerUiController(ChatModel chatModel, NutritionPlannerAgent nutritionPlannerAgent) {
        this.chatModel = chatModel;
        this.nutritionPlannerAgent = nutritionPlannerAgent;
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
        var weeklyPlan = nutritionPlannerAgent.createNutritionPlan(principal.getName(), request);

        model.addAttribute("plan", weeklyPlan);
        model.addAttribute("aiModel", getAiModelName());
        return "fragments/plan :: plan";
    }

    private String getAiModelName() {
        var chatModelProvider = chatModel.getClass().getSimpleName().replace("ChatModel", "");
        var chatModelDefaultOptions = chatModel.defaultRequestParameters();
        try {
            var modelName = (String)FieldUtils.readField(chatModelDefaultOptions, "modelName", true);
            return "%s (%s)".formatted(chatModelProvider, capitalize(modelName));
        } catch (Exception e1) {
            try {
                var modelName = (String)FieldUtils.readField(chatModelDefaultOptions, "deploymentName", true);
                return "%s (%s)".formatted(chatModelProvider, capitalize(modelName));
            } catch (Exception e2) {
                return chatModelProvider;
            }
        }
    }
}
