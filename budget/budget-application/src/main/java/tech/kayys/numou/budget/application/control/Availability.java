package tech.kayys.numou.budget.application.control;

import tech.kayys.numou.budget.domain.BudgetControlOutcome;

import java.math.BigDecimal;

public record Availability(BudgetControlOutcome outcome, BigDecimal allocated, BigDecimal reserved,
                           BigDecimal spent, BigDecimal available, String message) {}
