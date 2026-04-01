package com.example.nutrition_planner;
import java.util.List;

public record Recipe(
        String name,
        List<Ingredient> ingredients,
        NutritionInfo nutrition,
        String instructions,
        int prepTimeMinutes
) {}
