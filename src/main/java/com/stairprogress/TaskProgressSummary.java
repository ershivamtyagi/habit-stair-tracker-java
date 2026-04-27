package com.stairprogress;

public record TaskProgressSummary(
        int completedLevels,
        int totalLevels
) {
    public int remainingLevels() {
        return Math.max(0, totalLevels - completedLevels);
    }
}
