package com.example.nutrition_planner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.Verbosity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/nutrition-plan")
public class NutritionPlannerController {

    private final AgentPlatform agentPlatform;

    public NutritionPlannerController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @PostMapping
    public ResponseEntity<WeeklyPlan> createNutritionPlan(@RequestBody WeeklyPlanRequest request, Principal principal) {
        var invocation = AgentInvocation.builder(agentPlatform)
                // .options(ProcessOptions.DEFAULT.withVerbosity(
                //        Verbosity.DEFAULT.withDebug(true).withShowPlanning(true).withShowLlmResponses(true).withShowPrompts(true)))
                .build(WeeklyPlan.class);

        var inputs = Map.of(
                "user", principal.getName(),
                "request", request
        );
        var weeklyPlan = invocation.invoke(inputs);

        return ResponseEntity.ok(weeklyPlan);
    }
}