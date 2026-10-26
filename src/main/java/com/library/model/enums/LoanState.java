package com.library.model.enums;

/**
 * Derived loan state used by the UI.
 *
 * <p>This value is computed from {@code due_date} and {@code return_date}; it is never
 * stored in the {@code loans.status} column.
 */
public enum LoanState {
    ACTIVE,
    OVERDUE,
    RETURNED
}
