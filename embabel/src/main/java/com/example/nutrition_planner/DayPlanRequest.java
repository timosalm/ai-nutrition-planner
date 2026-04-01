package com.example.nutrition_planner;

import java.time.DayOfWeek;
import java.util.List;

public record DayPlanRequest(
        DayOfWeek day,
        List<MealType> meals
) {
}