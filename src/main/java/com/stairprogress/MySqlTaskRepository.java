package com.stairprogress;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

public final class MySqlTaskRepository {
    private final DatabaseConfig config;

    public MySqlTaskRepository(DatabaseConfig config) {
        this.config = config;
        initializeSchema();
    }

    public List<Task> loadTasks() {
        String sql = """
                SELECT id, name, start_date, created_at
                FROM tasks
                ORDER BY start_date DESC, created_at DESC
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Task> tasks = new ArrayList<>();
            while (resultSet.next()) {
                tasks.add(new Task(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getDate("start_date").toLocalDate(),
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                ));
            }
            return tasks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load tasks from MySQL.", exception);
        }
    }

    public Task createTask(String name, int year) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Task name is required.");
        }

        try (Connection connection = openDatabaseConnection()) {
            connection.setAutoCommit(false);

            Task task = insertTask(connection, normalizedName);
            ensureWeeklyLevelsForYear(connection, task.id(), year);

            connection.commit();
            return task;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create task in MySQL.", exception);
        }
    }

    public boolean deleteTask(long taskId) {
        String sql = """
                DELETE FROM tasks
                WHERE id = ?
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete task from MySQL.", exception);
        }
    }

    public List<WeeklyLevel> loadLevels(long taskId, int year) {
        ensureWeeklyLevelsForYear(taskId, year);
        String sql = """
                SELECT id, task_id, level_year, week_number, title, description, completed, completed_at
                FROM weekly_levels
                WHERE task_id = ? AND level_year = ?
                ORDER BY week_number
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setInt(2, year);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<WeeklyLevel> levels = new ArrayList<>();
                while (resultSet.next()) {
                    Timestamp completedAt = resultSet.getTimestamp("completed_at");
                    levels.add(new WeeklyLevel(
                            resultSet.getLong("id"),
                            resultSet.getLong("task_id"),
                            resultSet.getInt("level_year"),
                            resultSet.getInt("week_number"),
                            resultSet.getString("title"),
                            resultSet.getString("description"),
                            resultSet.getBoolean("completed"),
                            completedAt == null ? null : completedAt.toLocalDateTime()
                    ));
                }
                return levels;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load weekly levels from MySQL.", exception);
        }
    }

    public void ensureWeeklyLevelsForYear(long taskId, int year) {
        try (Connection connection = openDatabaseConnection()) {
            connection.setAutoCommit(false);
            ensureWeeklyLevelsForYear(connection, taskId, year);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to prepare weekly levels for the year.", exception);
        }
    }

    public List<DailyCompletion> loadDailyCompletions(long taskId) {
        String sql = """
                SELECT id, task_id, completion_date, completed_at, note
                FROM daily_completions
                WHERE task_id = ?
                ORDER BY completion_date DESC
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<DailyCompletion> completions = new ArrayList<>();
                while (resultSet.next()) {
                    completions.add(new DailyCompletion(
                            resultSet.getLong("id"),
                            resultSet.getLong("task_id"),
                            resultSet.getDate("completion_date").toLocalDate(),
                            resultSet.getTimestamp("completed_at").toLocalDateTime(),
                            resultSet.getString("note")
                    ));
                }
                return completions;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load daily completions from MySQL.", exception);
        }
    }

    public boolean hasDailyCompletion(long taskId, LocalDate date) {
        String sql = """
                SELECT 1
                FROM daily_completions
                WHERE task_id = ? AND completion_date = ?
                LIMIT 1
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check daily completion.", exception);
        }
    }

    public boolean toggleDailyCompletion(long taskId, String taskName, LocalDate date, LocalDate taskStartDate, String note) {
        try (Connection connection = openDatabaseConnection()) {
            connection.setAutoCommit(false);
            int weekYear = date.get(WeekFields.ISO.weekBasedYear());
            int weekNumber = taskWeekNumber(date, taskStartDate);
            ensureWeeklyLevelsForYear(connection, taskId, weekYear);

            boolean alreadyCompleted = hasDailyCompletion(connection, taskId, date);
            if (alreadyCompleted) {
                deleteDailyCompletion(connection, taskId, date);
                resetLevel(connection, taskId, weekNumber, weekYear);
                connection.commit();
                return false;
            }

            insertDailyCompletion(connection, taskId, date, note);
            if (isWeekFullyCompleted(connection, taskId, date, taskStartDate)) {
                completeLevel(connection, taskId, taskName, weekYear, weekNumber);
            }

            connection.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update daily completion.", exception);
        }
    }

    public boolean updateDailyCompletionNote(long taskId, LocalDate date, String note) {
        String sql = """
                UPDATE daily_completions
                SET note = ?
                WHERE task_id = ? AND completion_date = ?
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, note == null || note.isBlank() ? null : note.trim());
            statement.setLong(2, taskId);
            statement.setDate(3, java.sql.Date.valueOf(date));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update daily note.", exception);
        }
    }

    private int taskWeekNumber(LocalDate date, LocalDate taskStartDate) {
        long days = ChronoUnit.DAYS.between(taskStartDate, date);
        if (days < 0) days = 0;
        return (int) Math.min(days / 7 + 1, 52);
    }

    public TaskProgressSummary loadTaskProgressSummary(long taskId, int year) {
        String sql = """
                SELECT COUNT(*) AS total_count,
                       SUM(CASE WHEN completed THEN 1 ELSE 0 END) AS completed_count
                FROM weekly_levels
                WHERE task_id = ? AND level_year = ?
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setInt(2, year);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new TaskProgressSummary(
                            resultSet.getInt("completed_count"),
                            resultSet.getInt("total_count")
                    );
                }
                return new TaskProgressSummary(0, 52);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to calculate task progress.", exception);
        }
    }

    public boolean updateWeeklyLevelDetails(long taskId, int year, int weekNumber, String title, String description) {
        String normalizedTitle = title == null || title.isBlank()
                ? buildDefaultLevelTitle(weekNumber)
                : title.trim();
        String normalizedDescription = description == null || description.isBlank()
                ? buildDefaultLevelDescription(weekNumber)
                : description.trim();

        String sql = """
                UPDATE weekly_levels
                SET title = ?, description = ?
                WHERE task_id = ? AND level_year = ? AND week_number = ?
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedTitle);
            statement.setString(2, normalizedDescription);
            statement.setLong(3, taskId);
            statement.setInt(4, year);
            statement.setInt(5, weekNumber);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update weekly milestone details.", exception);
        }
    }

    public boolean completeLevel(long taskId, String taskName, int year, int weekNumber) {
        String updateLevelSql = """
                UPDATE weekly_levels
                SET completed = TRUE, completed_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND level_year = ? AND week_number = ? AND completed = FALSE
                """;

        String insertBadgeSql = """
                INSERT INTO profile_badges (task_id, level_year, week_number, badge_label, awarded_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (Connection connection = openDatabaseConnection()) {
            connection.setAutoCommit(false);

            int updatedRows;
            try (PreparedStatement updateStatement = connection.prepareStatement(updateLevelSql)) {
                updateStatement.setLong(1, taskId);
                updateStatement.setInt(2, year);
                updateStatement.setInt(3, weekNumber);
                updatedRows = updateStatement.executeUpdate();
            }

            if (updatedRows == 0) {
                connection.rollback();
                return false;
            }

            try (PreparedStatement badgeStatement = connection.prepareStatement(insertBadgeSql)) {
                badgeStatement.setLong(1, taskId);
                badgeStatement.setInt(2, year);
                badgeStatement.setInt(3, weekNumber);
                badgeStatement.setString(4, buildBadgeLabel(taskName, weekNumber));
                badgeStatement.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to complete the weekly level.", exception);
        }
    }

    public boolean resetLevel(long taskId, int year, int weekNumber) {
        String deleteBadgeSql = """
                DELETE FROM profile_badges
                WHERE task_id = ? AND level_year = ? AND week_number = ?
                """;

        String updateLevelSql = """
                UPDATE weekly_levels
                SET completed = FALSE, completed_at = NULL
                WHERE task_id = ? AND level_year = ? AND week_number = ? AND completed = TRUE
                """;

        try (Connection connection = openDatabaseConnection()) {
            connection.setAutoCommit(false);

            int updatedRows;
            try (PreparedStatement updateStatement = connection.prepareStatement(updateLevelSql)) {
                updateStatement.setLong(1, taskId);
                updateStatement.setInt(2, year);
                updateStatement.setInt(3, weekNumber);
                updatedRows = updateStatement.executeUpdate();
            }

            if (updatedRows == 0) {
                connection.rollback();
                return false;
            }

            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteBadgeSql)) {
                deleteStatement.setLong(1, taskId);
                deleteStatement.setInt(2, year);
                deleteStatement.setInt(3, weekNumber);
                deleteStatement.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reset the weekly level.", exception);
        }
    }

    public ProfileSummary loadProfileSummary(int year) {
        int taskCount = countTasks();
        int badgeCount = countBadges();
        int completedLevelCount = countCompletedLevels(year);
        List<ProfileBadge> recentBadges = loadRecentBadges();
        return new ProfileSummary(taskCount, completedLevelCount, badgeCount, recentBadges);
    }

    private void initializeSchema() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("MySQL JDBC driver is missing.", exception);
        }

        try (Connection connection = openServerConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + config.database());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create the MySQL database.", exception);
        }

        try (Connection connection = openDatabaseConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL UNIQUE,
                        start_date DATE NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            if (!columnExists(connection, "tasks", "start_date")) {
                statement.execute("""
                        ALTER TABLE tasks
                        ADD COLUMN start_date DATE NULL
                        """);
                statement.execute("""
                        UPDATE tasks
                        SET start_date = DATE(created_at)
                        WHERE start_date IS NULL
                        """);
                statement.execute("""
                        ALTER TABLE tasks
                        MODIFY COLUMN start_date DATE NOT NULL
                        """);
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS weekly_levels (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        task_id BIGINT NOT NULL,
                        level_year INT NOT NULL,
                        week_number INT NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        description TEXT NOT NULL,
                        completed BOOLEAN NOT NULL DEFAULT FALSE,
                        completed_at TIMESTAMP NULL,
                        CONSTRAINT fk_weekly_levels_task
                            FOREIGN KEY (task_id) REFERENCES tasks(id)
                            ON DELETE CASCADE,
                        CONSTRAINT uq_weekly_level UNIQUE (task_id, level_year, week_number)
                    )
                    """);
            if (!columnExists(connection, "weekly_levels", "description")) {
                statement.execute("""
                        ALTER TABLE weekly_levels
                        ADD COLUMN description TEXT NULL
                        """);
                statement.execute("""
                        UPDATE weekly_levels
                        SET description = CONCAT(
                                'Complete all 7 daily stairs in week ',
                                week_number,
                                ' to lock in the milestone and earn the weekly achievement badge.'
                        )
                        WHERE description IS NULL OR description = ''
                        """);
                statement.execute("""
                        ALTER TABLE weekly_levels
                        MODIFY COLUMN description TEXT NOT NULL
                        """);
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS daily_completions (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        task_id BIGINT NOT NULL,
                        completion_date DATE NOT NULL,
                        completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        note TEXT NULL,
                        CONSTRAINT fk_daily_completions_task
                            FOREIGN KEY (task_id) REFERENCES tasks(id)
                            ON DELETE CASCADE,
                        CONSTRAINT uq_daily_completion UNIQUE (task_id, completion_date)
                    )
                    """);
            if (!columnExists(connection, "daily_completions", "note")) {
                statement.execute("ALTER TABLE daily_completions ADD COLUMN note TEXT NULL");
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS profile_badges (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        task_id BIGINT NOT NULL,
                        level_year INT NOT NULL,
                        week_number INT NOT NULL,
                        badge_label VARCHAR(255) NOT NULL,
                        awarded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_profile_badges_task
                            FOREIGN KEY (task_id) REFERENCES tasks(id)
                            ON DELETE CASCADE,
                        CONSTRAINT uq_badge_per_level UNIQUE (task_id, level_year, week_number)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize MySQL tables.", exception);
        }
    }

    private Connection openServerConnection() throws SQLException {
        return DriverManager.getConnection(config.serverJdbcUrl(), config.username(), config.password());
    }

    private Connection openDatabaseConnection() throws SQLException {
        return DriverManager.getConnection(config.databaseJdbcUrl(), config.username(), config.password());
    }

    private Task insertTask(Connection connection, String name) throws SQLException {
        LocalDate startDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String sql = """
                INSERT INTO tasks (name, start_date, created_at)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setDate(2, java.sql.Date.valueOf(startDate));
            statement.setTimestamp(3, Timestamp.valueOf(now));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Task insert did not return a generated id.");
                }
                return new Task(keys.getLong(1), name, startDate, now);
            }
        }
    }

    private void ensureWeeklyLevelsForYear(Connection connection, long taskId, int year) throws SQLException {
        String countSql = """
                SELECT COUNT(*)
                FROM weekly_levels
                WHERE task_id = ? AND level_year = ?
                """;

        try (PreparedStatement countStatement = connection.prepareStatement(countSql)) {
            countStatement.setLong(1, taskId);
            countStatement.setInt(2, year);
            try (ResultSet resultSet = countStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) >= 52) {
                    return;
                }
            }
        }

        String sql = """
                INSERT INTO weekly_levels (task_id, level_year, week_number, title, description, completed, completed_at)
                VALUES (?, ?, ?, ?, ?, FALSE, NULL)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int week = 1; week <= 52; week++) {
                statement.setLong(1, taskId);
                statement.setInt(2, year);
                statement.setInt(3, week);
                statement.setString(4, buildDefaultLevelTitle(week));
                statement.setString(5, buildDefaultLevelDescription(week));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean hasDailyCompletion(Connection connection, long taskId, LocalDate date) throws SQLException {
        String sql = """
                SELECT 1
                FROM daily_completions
                WHERE task_id = ? AND completion_date = ?
                LIMIT 1
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void insertDailyCompletion(Connection connection, long taskId, LocalDate date, String note) throws SQLException {
        String sql = """
                INSERT INTO daily_completions (task_id, completion_date, completed_at, note)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(4, note);
            statement.executeUpdate();
        }
    }

    private void deleteDailyCompletion(Connection connection, long taskId, LocalDate date) throws SQLException {
        String sql = """
                DELETE FROM daily_completions
                WHERE task_id = ? AND completion_date = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            statement.executeUpdate();
        }
    }

    private boolean isWeekFullyCompleted(Connection connection, long taskId, LocalDate date, LocalDate taskStartDate) throws SQLException {
        long days = Math.max(0, ChronoUnit.DAYS.between(taskStartDate, date));
        long weekIndex = days / 7;
        LocalDate weekStart = taskStartDate.plusDays(weekIndex * 7);
        LocalDate weekEnd = weekStart.plusDays(6);

        String sql = """
                SELECT COUNT(*)
                FROM daily_completions
                WHERE task_id = ? AND completion_date BETWEEN ? AND ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            statement.setDate(2, java.sql.Date.valueOf(weekStart));
            statement.setDate(3, java.sql.Date.valueOf(weekEnd));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) >= 7;
            }
        }
    }

    private void completeLevel(Connection connection, long taskId, String taskName, int year, int weekNumber) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String updateLevelSql = """
                UPDATE weekly_levels
                SET completed = TRUE, completed_at = ?
                WHERE task_id = ? AND level_year = ? AND week_number = ? AND completed = FALSE
                """;

        String insertBadgeSql = """
                INSERT INTO profile_badges (task_id, level_year, week_number, badge_label, awarded_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE badge_label = VALUES(badge_label)
                """;

        try (PreparedStatement updateStatement = connection.prepareStatement(updateLevelSql)) {
            updateStatement.setTimestamp(1, now);
            updateStatement.setLong(2, taskId);
            updateStatement.setInt(3, year);
            updateStatement.setInt(4, weekNumber);
            updateStatement.executeUpdate();
        }

        try (PreparedStatement badgeStatement = connection.prepareStatement(insertBadgeSql)) {
            badgeStatement.setLong(1, taskId);
            badgeStatement.setInt(2, year);
            badgeStatement.setInt(3, weekNumber);
            badgeStatement.setString(4, buildBadgeLabel(taskName, weekNumber));
            badgeStatement.setTimestamp(5, now);
            badgeStatement.executeUpdate();
        }
    }

    private String buildBadgeLabel(String taskName, int weekNumber) {
        String normalizedTaskName = taskName == null ? "Task" : taskName.trim();
        if (normalizedTaskName.isEmpty()) {
            normalizedTaskName = "Task";
        }
        return normalizedTaskName + " Consistency Milestone - Week " + weekNumber;
    }

    private String buildDefaultLevelTitle(int weekNumber) {
        return "Week " + weekNumber + " Consistency Goal";
    }

    private String buildDefaultLevelDescription(int weekNumber) {
        return "Complete all 7 daily stairs in week " + weekNumber
                + " to lock in the milestone and earn the weekly achievement badge.";
    }

    private void resetLevel(Connection connection, long taskId, int weekNumber, int year) throws SQLException {
        String deleteBadgeSql = """
                DELETE FROM profile_badges
                WHERE task_id = ? AND level_year = ? AND week_number = ?
                """;

        String updateLevelSql = """
                UPDATE weekly_levels
                SET completed = FALSE, completed_at = NULL
                WHERE task_id = ? AND level_year = ? AND week_number = ?
                """;

        try (PreparedStatement deleteStatement = connection.prepareStatement(deleteBadgeSql)) {
            deleteStatement.setLong(1, taskId);
            deleteStatement.setInt(2, year);
            deleteStatement.setInt(3, weekNumber);
            deleteStatement.executeUpdate();
        }

        try (PreparedStatement updateStatement = connection.prepareStatement(updateLevelSql)) {
            updateStatement.setLong(1, taskId);
            updateStatement.setInt(2, year);
            updateStatement.setInt(3, weekNumber);
            updateStatement.executeUpdate();
        }
    }

    private int countTasks() {
        return runCountQuery("SELECT COUNT(*) FROM tasks");
    }

    private int countBadges() {
        return runCountQuery("SELECT COUNT(*) FROM profile_badges");
    }

    private int countCompletedLevels(int year) {
        String sql = """
                SELECT COUNT(*)
                FROM weekly_levels
                WHERE level_year = ? AND completed = TRUE
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count completed levels.", exception);
        }
    }

    private int runCountQuery(String sql) {
        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute count query.", exception);
        }
    }

    private List<ProfileBadge> loadRecentBadges() {
        String sql = """
                SELECT pb.id, pb.task_id, t.name, pb.level_year, pb.week_number, pb.badge_label, pb.awarded_at
                FROM profile_badges pb
                INNER JOIN tasks t ON t.id = pb.task_id
                ORDER BY pb.awarded_at DESC
                LIMIT 8
                """;

        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<ProfileBadge> badges = new ArrayList<>();
            while (resultSet.next()) {
                badges.add(new ProfileBadge(
                        resultSet.getLong("id"),
                        resultSet.getLong("task_id"),
                        resultSet.getString("name"),
                        resultSet.getInt("level_year"),
                        resultSet.getInt("week_number"),
                        resultSet.getString("badge_label"),
                        resultSet.getTimestamp("awarded_at").toLocalDateTime()
                ));
            }
            return badges;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load profile badges.", exception);
        }
    }
}
