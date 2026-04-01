package com.example.nutrition_planner;

import java.util.List;

public record NutritionAuditValidationResult(
        boolean allPassed,
        List<NutritionAuditRecipeViolation> violations,
        String consolidatedFeedback
) {}
