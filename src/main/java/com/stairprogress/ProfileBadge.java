package com.stairprogress;

import java.time.LocalDateTime;

public record ProfileBadge(
        long id,
        long taskId,
        String taskName,
        int levelYear,
        int weekNumber,
        String badgeLabel,
        LocalDateTime awardedAt
) {
}
