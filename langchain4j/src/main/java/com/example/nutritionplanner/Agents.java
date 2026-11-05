package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.*;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.Skills;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

interface Agents {

    /**
     * Typed entry point for the composed agentic nutrition planning workflow
     */
    interface NutritionPlanner {

        // Non-Declarative API: AgenticServices.sequenceBuilder(Agents.NutritionPlanner.class)
        @SequenceAgent(description = "Creates a validated weekly nutrition plan", typedOutputKey = WeeklyPlan.class,
                subAgents = {PlanInputsFetcher.class, WeeklyValidatedPlanCreator.class})
        WeeklyPlan createNutritionPlan(
                @V("username") String username,
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @V("country") String country,
                @V("additionalInstructions") String additionalInstructions);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return ApplicationContextProvider.getBean(ChatModel.class);
        }

        @AgentListenerSupplier
        static AgentListener composedAgentListener() {
            var meterRegistry = ApplicationContextProvider.getBean(MeterRegistry.class);
            var agentMonitor = ApplicationContextProvider.getBean(AgentMonitor.class);
            return new ComposedAgentListener(new AgentListeners.LoggingListener(),
                    new AgentListeners.MicrometerAgentListener(meterRegistry),
                    agentMonitor);
        }
    }

    interface PlanInputsFetcher {

        // Non-Declarative API: AgenticServices.parallelBuilder()
        @ParallelAgent(description = "Fetches the user profile and seasonal ingredients in parallel",
                subAgents = {UserProfileAgent.class, SeasonalIngredientAgent.class})
        void fetchPlanInputs();

        @ParallelExecutor
        static Executor executor() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    // Non-AI agent
    // Non-Declarative API: AgenticServices.nonAiAgentBuilder()
    class UserProfileAgent {

        @Agent(description = "Fetches the profile of a user", typedOutputKey = UserProfile.class)
        public static UserProfile fetchUserProfile(@V("username") String username) {
            return ApplicationContextProvider.getBean(UserProfileProperties.class).getUserProfile(username);
        }
    }

    interface SeasonalIngredientAgent {

        // Non-Declarative API: AgenticServices.agentBuilder(Agents.SeasonalIngredientAgent.class)
        @UserMessage("""
            You are a nutrition expert with deep knowledge of seasonal produce.

            Use the available skill to determine the current month, then return a list of ingredients in English
            that are currently in season for that month in {{country}}.
            Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
            """)
        @Agent(description = "Fetches seasonal ingredients for a given location and time of year",
                typedOutputKey = SeasonalIngredients.class)
        SeasonalIngredients fetchSeasonalIngredients(@V("country") String country);

        @SystemMessageProviderSupplier
        static String systemMessage(Object memoryId) {
            return """
                You have access to the following skills:
                %s
                When the user's request relates to one of these skills, activate it first.
                """.formatted(skills().formatAvailableSkills());
        }

        @ToolProviderSupplier
        static ToolProvider toolProvider() {
            return skills().toolProvider();
        }

        private static Skills skills() {
            return Skills.from(CustomSkillLoader.loadSkills("skills"));
        }
    }

    interface WeeklyValidatedPlanCreator {

        // Non-Declarative API: AgenticServices.loopBuilder()
        @LoopAgent(subAgents = {WeeklyPlanCreator.class, NutritionGuard.class}, typedOutputKey = WeeklyPlan.class,
                maxIterations = 3)
        WeeklyPlan createValidatedWeeklyPlan();

        @ExitCondition(testExitAtLoopEnd = true, description = "score greater than 0.8")
        static boolean exit(@K(NutritionAuditValidationResult.class) NutritionAuditValidationResult validationResult) {
            return validationResult.allPassed();
        }
    }

    interface WeeklyPlanCreator {

        @SystemMessage(Personas.RECIPE_CURATOR)
        @UserMessage("""
            Create a weekly meal plan based on the following inputs:

            # User requested meals and days
            {{WeeklyPlanRequest}}

            # Seasonal ingredients
            {{SeasonalIngredients}}

            # User profile (dietary restrictions, allergies, preferences)
            {{UserProfile}}

            # Additional instructions
            {{additionalInstructions}}
            
            If available, don't create and revise this weekly meal plan based on the following feedback instead:

            # Current response
            {{WeeklyPlan}}

            # Feedback
            {{NutritionAuditValidationResult}}
            """)
        @Agent(description = "Creates a weekly meal plan using seasonal ingredients and user preferences",
                typedOutputKey = WeeklyPlan.class)
        WeeklyPlan createWeeklyPlan(
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @K(SeasonalIngredients.class) SeasonalIngredients seasonalIngredients,
                @K(UserProfile.class) UserProfile userProfile,
                @V("additionalInstructions") String additionalInstructions,
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(NutritionAuditValidationResult.class) NutritionAuditValidationResult validationResult);
    }

    interface NutritionGuard {

        @SystemMessage(Personas.NUTRITION_GUARD)
        @UserMessage("""
            Validate these recipes against the user profile and flag any violations.

            # Recipes
            {{WeeklyPlan}}

            # User profile
            {{UserProfile}}
            """)
        @ExitCondition
        @Agent(description = "Validates a weekly plan against user dietary requirements",
                typedOutputKey = NutritionAuditValidationResult.class)
        NutritionAuditValidationResult validate(
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(UserProfile.class) UserProfile userProfile);


        @ToolsSupplier
        static Object[] tools() {
            return new Object[] { new WeeklyPlan() };
        }
    }

    interface Personas {
        String RECIPE_CURATOR = """
                You are a Recipe Curator.
                Your persona: A culinary expert specializing in weekly meal planning.
                Your voice: Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
                and always provide accurate nutrition information for each dish.
                Your objective is to draft recipes in English based on the user requested meals and days.
                Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                """;

        String NUTRITION_GUARD = """
                You are a Nutrition Guard.
                Your persona: A strict dietary compliance validator
                specialized in ensuring meal plans meet user health requirements and dietary restrictions.
                Your voice: Thorough, precise, and uncompromising. You apply dietary rules consistently and
                flag every violation without exception. Be concise and factual in your assessments.
                Your objective is to validate a list of recipes against a user profile and flag any violations.
                Check each recipe for:
                1. NUTRITION_INFO: Nutrition information is available for each recipe
                2. CALORIE_OVERFLOW: calories exceed daily calorie target
                3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
                4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
                5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
                """;
    }
}
