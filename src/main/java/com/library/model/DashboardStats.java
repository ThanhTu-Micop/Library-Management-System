package com.library.model;

/**
 * Six real dashboard metrics.
 * Person 1 supplies the SQL/DAO; Person 2 decides how the service exposes them to the view.
 */
public class DashboardStats {

    private long totalBooks;
    private long totalMembers;
    private long totalCopies;
    private long activeLoans;
    private long overdueLoans;
    private long availableCopies;

    public DashboardStats() {
    }

    public DashboardStats(long totalBooks, long totalMembers, long totalCopies, long activeLoans, long overdueLoans, long availableCopies) {
        this.totalBooks = totalBooks;
        this.totalMembers = totalMembers;
        this.totalCopies = totalCopies;
        this.activeLoans = activeLoans;
        this.overdueLoans = overdueLoans;
        this.availableCopies = availableCopies;
    }

    public long getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(long totalBooks) {
        this.totalBooks = totalBooks;
    }

    public long getTotalMembers() {
        return totalMembers;
    }

    public void setTotalMembers(long totalMembers) {
        this.totalMembers = totalMembers;
    }

    public long getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(long totalCopies) {
        this.totalCopies = totalCopies;
    }

    public long getActiveLoans() {
        return activeLoans;
    }

    public void setActiveLoans(long activeLoans) {
        this.activeLoans = activeLoans;
    }

    public long getOverdueLoans() {
        return overdueLoans;
    }

    public void setOverdueLoans(long overdueLoans) {
        this.overdueLoans = overdueLoans;
    }

    public long getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(long availableCopies) {
        this.availableCopies = availableCopies;
    }

    @Override
    public String toString() {
        return "DashboardStats{totalBooks=" + totalBooks + ", totalMembers=" + totalMembers + ", totalCopies=" + totalCopies
                + ", activeLoans=" + activeLoans + ", overdueLoans=" + overdueLoans + ", availableCopies=" + availableCopies + "}";
    }
}
