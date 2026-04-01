package com.example.nutrition_planner;

import java.util.List;

public record WeeklyPlan(
        List<DailyPlan> days,
        NutritionInfo weeklyAverages
) {}
