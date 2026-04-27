package com.stairprogress;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActivityLog(LocalDate date, String activityName, LocalDateTime completedAt) {
}
