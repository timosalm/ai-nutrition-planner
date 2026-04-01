package com.example.nutrition_planner;

import java.util.List;

public record WeeklyPlanRequest(List<DayPlanRequest> days, String countryCode) { }