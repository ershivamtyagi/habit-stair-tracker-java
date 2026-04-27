package com.stairprogress;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyCompletion(
        long id,
        long taskId,
        LocalDate completionDate,
        LocalDateTime completedAt,
        String note
) {
}
