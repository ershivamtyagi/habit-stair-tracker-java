package com.stairprogress;

import java.util.List;

public record ProfileSummary(
        int taskCount,
        int completedLevelCount,
        int badgeCount,
        List<ProfileBadge> recentBadges
) {
}
