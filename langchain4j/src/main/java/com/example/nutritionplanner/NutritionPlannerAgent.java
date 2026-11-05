package com.example.nutritionplanner;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final AgentMonitor agentMonitor;

    NutritionPlannerAgent(AgentMonitor agentMonitor) {
        this.agentMonitor = agentMonitor;
    }

    WeeklyPlan createNutritionPlan(String username, WeeklyPlanRequest request) {
        log.info("Starting meal plan creation for user: {}", username);
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);

        var weeklyPlan = AgenticServices.createAgenticSystem(Agents.NutritionPlanner.class)
                .createNutritionPlan(username, request, country, request.additionalInstructions());
        log.info("NutritionPlannerAgent finished with agents invocations {}", agentMonitor.successfulExecutions().getFirst());
        return weeklyPlan;
    }
}
