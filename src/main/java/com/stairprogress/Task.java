package com.stairprogress;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Task(long id, String name, LocalDate startDate, LocalDateTime createdAt) {
    @Override
    public String toString() {
        return name;
    }
}
