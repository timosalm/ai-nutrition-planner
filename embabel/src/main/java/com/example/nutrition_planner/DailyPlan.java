package com.example.nutrition_planner;

import java.time.DayOfWeek;
import java.util.Optional;
import java.util.stream.Stream;

public record DailyPlan(
        DayOfWeek day,
        Optional<Recipe> breakfast,
        Optional<Recipe> lunch,
        Optional<Recipe> dinner
) {
}
