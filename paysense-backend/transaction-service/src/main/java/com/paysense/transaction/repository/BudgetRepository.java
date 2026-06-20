package com.paysense.transaction.repository;

import com.paysense.transaction.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    /**
     * Find all budgets for a user in a specific month/year.
     */
    List<Budget> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);

    /**
     * Find a specific budget for upsert logic.
     */
    Optional<Budget> findByUserIdAndCategoryAndMonthAndYear(UUID userId, String category,
                                                             Integer month, Integer year);

    /**
     * Find all budgets for a user.
     */
    List<Budget> findByUserId(UUID userId);
}
