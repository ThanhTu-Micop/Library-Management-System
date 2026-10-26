package com.library.model.enums;

/**
 * Stored loan status. Values mirror the database {@code CHECK} constraint.
 *
 * <p>Overdue is not stored; it is derived from {@code due_date} and {@code return_date}.
 */
public enum LoanStatus {
    ACTIVE,
    RETURNED
}
