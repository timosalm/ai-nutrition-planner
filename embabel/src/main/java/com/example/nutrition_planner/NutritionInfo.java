package com.example.nutrition_planner;

import static java.util.Arrays.*;

import java.util.List;
import java.util.Objects;

public record NutritionInfo(
        int calories,
        double proteinGrams,
        double carbGrams,
        double fatGrams,
        int sodiumMg
) {
    public NutritionInfo(List<Recipe> recipes) {
        this(recipes.stream().mapToInt(r -> r.nutrition().calories()).sum(),
            recipes.stream().mapToDouble(r -> r.nutrition().proteinGrams()).sum(),
            recipes.stream().mapToDouble(r -> r.nutrition().carbGrams()).sum(),
            recipes.stream().mapToDouble(r -> r.nutrition().fatGrams()).sum(),
            recipes.stream().mapToInt(r -> r.nutrition().sodiumMg()).sum()
        );
    }
}
