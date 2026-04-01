package com.example.nutrition_planner;

import java.time.LocalDate;

public record Ingredient(
        String name,
        String quantity,
        String unit
) {}
