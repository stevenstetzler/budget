package com.vidalabs.budget.data

data class DefaultCategory(val name: String, val isPositive: Boolean)

val DEFAULT_CATEGORIES: List<DefaultCategory> = listOf(
    DefaultCategory("income", true),
    DefaultCategory("paycheck", true),

    DefaultCategory("taxes", false),
    DefaultCategory("withholding", false),
    DefaultCategory("grocery", false),
    DefaultCategory("dining", false),
    DefaultCategory("debt", false),
    DefaultCategory("utilities", false),
    DefaultCategory("car", false),
    DefaultCategory("travel", false),
    DefaultCategory("health", false),
    DefaultCategory("subscriptions", false),
    DefaultCategory("housing", false),
    DefaultCategory("shopping", false),
    DefaultCategory("charity", false),
    DefaultCategory("misc", false),
    DefaultCategory("entertainment", false),
)
