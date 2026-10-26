package com.library.model.enums;

/**
 * Status of a physical book copy.
 *
 * <p>Values mirror the database {@code CHECK} constraint. {@code CopyStatus} describes a
 * copy, while {@code LoanStatus} describes a loan.
 */
public enum CopyStatus {
    AVAILABLE,
    BORROWED,
    MAINTENANCE
}
