package com.stairprogress;

import java.time.LocalDateTime;

public record WeeklyLevel(
        long id,
        long taskId,
        int levelYear,
        int weekNumber,
        String title,
        String description,
        boolean completed,
        LocalDateTime completedAt
) {
}
