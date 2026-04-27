package com.stairprogress;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class StairProgressApp extends Application {
    private static final int WINDOW_DAYS = StairGraphic.STEP_COUNT;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final MySqlTaskRepository repository = new MySqlTaskRepository(DatabaseConfig.load());
    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final ObservableList<DailyCompletion> recentCompletions = FXCollections.observableArrayList();

    private final Label streakLabel = new Label();
    private final Label headlineLabel = new Label();
    private final Label feedbackLabel = new Label();
    private final Label badgeLabel = new Label();
    private final Label weeklyProgressLabel = new Label();
    private final Label selectedTaskScopeLabel = new Label();
    private final Label badgeSectionLabel = new Label();
    private final Label latestBadgeLabel = new Label();
    private final Label milestoneTitleLabel = new Label();
    private final Label milestoneDescriptionLabel = new Label();
    private final Label quickNoteHintLabel = new Label();
    private final TextField milestoneTitleField = new TextField();
    private final TextArea milestoneDescriptionArea = new TextArea();
    private final TextField quickNoteField = new TextField();
    private final Button quickNoteButton = new Button("Add note");
    private final Button quickNoteSaveButton = new Button("Save");
    private final HBox quickNoteBar = new HBox(8);
    private final StairGraphic stairGraphic = new StairGraphic();
    private final ListView<Task> taskListView = new ListView<>(tasks);
    private final ListView<DailyCompletion> completionListView = new ListView<>(recentCompletions);
    private final FlowPane badgePane = new FlowPane();
    private final PauseTransition quickNoteDismiss = new PauseTransition(Duration.seconds(5));

    private long quickNoteTaskId = -1;
    private LocalDate quickNoteDate;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setPadding(new Insets(24));

        VBox content = new VBox(20, buildHeroSection(), buildMainArea());
        content.getStyleClass().add("content-stack");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("app-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stairGraphic.setOnStepClicked(this::toggleCompletionForDate);
        taskListView.getSelectionModel().selectedItemProperty().addListener((ignore, oldValue, newValue) -> {
            hideQuickNotePrompt();
            taskListView.refresh();
            if (newValue != null && (oldValue == null || oldValue.id() != newValue.id())) {
                renderSelectedTask(newValue);
            }
        });

        refreshDashboard();

        stage.setTitle("Stair Progress App");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private HBox buildHeroSection() {
        Label eyebrow = new Label("STAIR PROGRESS");
        eyebrow.getStyleClass().add("eyebrow");

        selectedTaskScopeLabel.getStyleClass().add("hero-title");
        selectedTaskScopeLabel.setWrapText(true);

        weeklyProgressLabel.getStyleClass().add("hero-copy");
        weeklyProgressLabel.setWrapText(true);

        VBox left = new VBox(4, eyebrow, selectedTaskScopeLabel, weeklyProgressLabel);
        HBox.setHgrow(left, Priority.ALWAYS);

        HBox stats = new HBox(10,
                buildStatCard("Streak", streakLabel),
                buildStatCard("Badges", badgeLabel));
        stats.setAlignment(Pos.CENTER_RIGHT);

        HBox hero = new HBox(24, left, stats);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("panel");
        hero.setPadding(new Insets(18, 24, 18, 24));
        return hero;
    }

    private VBox buildMainArea() {
        VBox leftPanel = new VBox(16, buildTaskList(), buildMilestonePanel(), buildBadgeStrip());
        leftPanel.getStyleClass().add("panel");
        leftPanel.setPadding(new Insets(24));
        leftPanel.setPrefWidth(430);
        leftPanel.setMinWidth(390);

        VBox rightPanel = new VBox(18);
        rightPanel.getStyleClass().add("panel");
        rightPanel.setPadding(new Insets(24));

        Label visualTitle = new Label("Progress Stair");
        visualTitle.getStyleClass().add("section-title");

        headlineLabel.getStyleClass().add("headline");
        headlineLabel.setWrapText(true);

        Label subText = new Label("Each step is one calendar day. Today's and yesterday's steps are clickable — add an optional note when marking complete. Finish all 7 days in a period to earn the badge.");
        subText.getStyleClass().add("muted-copy");
        subText.setWrapText(true);

        VBox recentDaysCard = buildTimeline();
        recentDaysCard.getStyleClass().addAll("support-card", "recent-days-side");
        recentDaysCard.setPrefWidth(280);
        recentDaysCard.setMinWidth(260);

        VBox stairColumn = new VBox(16, stairGraphic, buildQuickNoteBar());
        HBox.setHgrow(stairColumn, Priority.ALWAYS);

        HBox visualRow = new HBox(18, stairColumn, recentDaysCard);
        visualRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(visualRow, Priority.ALWAYS);

        rightPanel.getChildren().addAll(visualTitle, headlineLabel, subText, visualRow);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        HBox main = new HBox(20, leftPanel, rightPanel);
        main.getStyleClass().add("main-layout");
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        return new VBox(main);
    }

    private VBox buildTaskList() {
        Label title = new Label("Tasks");
        title.getStyleClass().add("section-title");

        Button newTaskBtn = new Button("+ New Task");
        newTaskBtn.getStyleClass().add("primary-button");
        newTaskBtn.setOnAction(e -> showCreateTaskDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, newTaskBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        taskListView.getStyleClass().add("task-list");
        taskListView.setPlaceholder(new Label("No tasks yet. Click + New Task to begin."));
        taskListView.setCellFactory(ignore -> new TaskCell(this::confirmAndDeleteTask, this::showHistoryDialog));
        taskListView.setFixedCellSize(50);
        taskListView.prefHeightProperty().bind(
                Bindings.max(54, Bindings.size(tasks).multiply(50).add(4)));
        taskListView.setMaxHeight(Double.MAX_VALUE);

        feedbackLabel.getStyleClass().add("feedback-copy");
        feedbackLabel.setWrapText(true);

        return new VBox(10, header, taskListView, feedbackLabel);
    }

    private void showCreateTaskDialog() {
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. workout, reading, study");
        nameField.getStyleClass().add("activity-input");

        Label infoLabel = new Label("Creates 52 weekly periods starting from today.");
        infoLabel.getStyleClass().add("muted-copy");
        infoLabel.setWrapText(true);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("feedback-copy");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox content = new VBox(12, infoLabel, nameField, errorLabel);
        content.setPrefWidth(380);
        content.setPadding(new Insets(4, 0, 4, 0));

        ButtonType createType = new ButtonType("Create Task", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Task");
        dialog.setHeaderText("Create a new task");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(createType).addEventFilter(
                javafx.event.ActionEvent.ACTION, event -> {
                    if (nameField.getText().trim().isEmpty()) {
                        errorLabel.setText("Task name is required.");
                        errorLabel.setVisible(true);
                        errorLabel.setManaged(true);
                        event.consume();
                    }
                });

        nameField.setOnAction(e -> ((Button) dialog.getDialogPane().lookupButton(createType)).fire());

        dialog.showAndWait().ifPresent(response -> {
            if (response == createType) {
                createTaskWithName(nameField.getText().trim());
            }
        });
    }

    private void createTaskWithName(String name) {
        try {
            Task task = repository.createTask(name, currentWeekYear());
            refreshDashboard();
            selectTask(task.id());
            setFeedback("Created '" + task.name() + "' with 52 weekly periods.", true);
        } catch (Exception exception) {
            setFeedback("Could not create task. Try a different name if it already exists.", false);
        }
    }

    private VBox buildMilestonePanel() {
        Label title = new Label("Current Milestone");
        title.getStyleClass().add("section-title");

        milestoneTitleLabel.getStyleClass().add("activity-title");
        milestoneTitleLabel.setWrapText(true);

        milestoneDescriptionLabel.getStyleClass().add("muted-copy");
        milestoneDescriptionLabel.setWrapText(true);

        milestoneTitleField.setPromptText("Milestone title");
        milestoneTitleField.getStyleClass().add("activity-input");

        milestoneDescriptionArea.setPromptText("Milestone description");
        milestoneDescriptionArea.getStyleClass().add("milestone-textarea");
        milestoneDescriptionArea.setWrapText(true);
        milestoneDescriptionArea.setPrefRowCount(3);

        Button saveButton = new Button("Save Milestone");
        saveButton.getStyleClass().add("secondary-button");
        saveButton.setOnAction(event -> saveCurrentMilestoneOverride());

        return new VBox(12, title, milestoneTitleLabel, milestoneDescriptionLabel, milestoneTitleField, milestoneDescriptionArea, saveButton);
    }

    private VBox buildTimeline() {
        Label title = new Label("Recent Days");
        title.getStyleClass().add("section-title");

        completionListView.getStyleClass().add("activity-list");
        completionListView.getStyleClass().add("recent-days-list");
        completionListView.setPlaceholder(new Label("Select a task to see recent daily progress."));
        completionListView.setCellFactory(ignore -> new DailyCompletionCell());
        completionListView.setPrefHeight(280);
        completionListView.setMaxHeight(280);
        VBox.setVgrow(completionListView, Priority.NEVER);

        VBox wrapper = new VBox(14, title, completionListView);
        return wrapper;
    }

    private VBox buildBadgeStrip() {
        badgeSectionLabel.getStyleClass().add("section-title");
        badgeSectionLabel.setText("Task Badges");

        badgePane.getStyleClass().add("badge-pane");
        badgePane.setHgap(10);
        badgePane.setVgap(10);

        return new VBox(12, badgeSectionLabel, badgePane);
    }

    private VBox buildStatCard(String labelText, Label valueLabel) {
        Label label = new Label(labelText);
        label.getStyleClass().add("stat-label");

        valueLabel.getStyleClass().add("stat-value");

        VBox card = new VBox(6, label, valueLabel);
        card.getStyleClass().add("stat-card");
        return card;
    }

    private HBox buildQuickNoteBar() {
        quickNoteBar.getStyleClass().add("inline-note-bar");
        quickNoteBar.setAlignment(Pos.CENTER_LEFT);
        quickNoteBar.setVisible(false);
        quickNoteBar.setManaged(false);

        quickNoteHintLabel.getStyleClass().add("inline-note-hint");

        quickNoteButton.getStyleClass().add("link-button");
        quickNoteButton.setOnAction(event -> expandQuickNoteInput());

        quickNoteField.setPromptText("Write a quick note");
        quickNoteField.getStyleClass().add("inline-note-field");
        quickNoteField.setVisible(false);
        quickNoteField.setManaged(false);
        quickNoteField.setOnAction(event -> saveQuickNote());

        quickNoteSaveButton.getStyleClass().add("secondary-button");
        quickNoteSaveButton.setVisible(false);
        quickNoteSaveButton.setManaged(false);
        quickNoteSaveButton.setOnAction(event -> saveQuickNote());

        quickNoteDismiss.setOnFinished(event -> {
            if (!quickNoteField.isFocused()) {
                hideQuickNotePrompt();
            }
        });

        quickNoteBar.getChildren().addAll(quickNoteHintLabel, quickNoteButton, quickNoteField, quickNoteSaveButton);
        return quickNoteBar;
    }

    private void toggleCompletionForDate(LocalDate date) {
        Task selectedTask = taskListView.getSelectionModel().getSelectedItem();
        if (selectedTask == null) {
            setFeedback("Select a task first.", false);
            return;
        }

        String dayLabel = date.equals(LocalDate.now()) ? "Today" : "Yesterday";
        LocalDate taskStart = selectedTask.startDate();

        if (repository.hasDailyCompletion(selectedTask.id(), date)) {
            hideQuickNotePrompt();
            repository.toggleDailyCompletion(selectedTask.id(), selectedTask.name(), date, taskStart, null);
            refreshDashboard();
            selectTask(selectedTask.id());
            setFeedback(dayLabel + "'s completion was cleared. If the period is no longer full, the badge was removed.", false);
            return;
        }

        repository.toggleDailyCompletion(selectedTask.id(), selectedTask.name(), date, taskStart, null);
        refreshDashboard();
        selectTask(selectedTask.id());
        setFeedback(dayLabel + " is complete. Finish all 7 days in this period to assign the badge.", true);
        showQuickNotePrompt(selectedTask, date, dayLabel);
    }

    private void showQuickNotePrompt(Task task, LocalDate date, String dayLabel) {
        quickNoteTaskId = task.id();
        quickNoteDate = date;
        quickNoteHintLabel.setText(dayLabel + " saved.");
        quickNoteField.clear();
        quickNoteButton.setVisible(true);
        quickNoteButton.setManaged(true);
        quickNoteField.setVisible(false);
        quickNoteField.setManaged(false);
        quickNoteSaveButton.setVisible(false);
        quickNoteSaveButton.setManaged(false);
        quickNoteBar.setVisible(true);
        quickNoteBar.setManaged(true);
        quickNoteDismiss.playFromStart();
    }

    private void expandQuickNoteInput() {
        quickNoteDismiss.stop();
        quickNoteButton.setVisible(false);
        quickNoteButton.setManaged(false);
        quickNoteField.setVisible(true);
        quickNoteField.setManaged(true);
        quickNoteSaveButton.setVisible(true);
        quickNoteSaveButton.setManaged(true);
        quickNoteField.requestFocus();
    }

    private void saveQuickNote() {
        Task selectedTask = taskListView.getSelectionModel().getSelectedItem();
        if (selectedTask == null || quickNoteDate == null || selectedTask.id() != quickNoteTaskId) {
            hideQuickNotePrompt();
            return;
        }

        boolean updated = repository.updateDailyCompletionNote(selectedTask.id(), quickNoteDate, quickNoteField.getText());
        if (updated) {
            refreshDashboard();
            selectTask(selectedTask.id());
            setFeedback("Note saved for " + DATE_FORMAT.format(quickNoteDate) + ".", true);
        }
        hideQuickNotePrompt();
    }

    private void hideQuickNotePrompt() {
        quickNoteDismiss.stop();
        quickNoteTaskId = -1;
        quickNoteDate = null;
        quickNoteField.clear();
        quickNoteBar.setVisible(false);
        quickNoteBar.setManaged(false);
        quickNoteButton.setVisible(true);
        quickNoteButton.setManaged(true);
        quickNoteField.setVisible(false);
        quickNoteField.setManaged(false);
        quickNoteSaveButton.setVisible(false);
        quickNoteSaveButton.setManaged(false);
    }

    private void confirmAndDeleteTask(Task task) {
        if (task == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Task");
        alert.setHeaderText("Delete " + task.name() + "?");
        alert.setContentText("This will remove the task, its daily stairs, weekly levels, and badges.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        Task currentSelection = taskListView.getSelectionModel().getSelectedItem();
        boolean removed = repository.deleteTask(task.id());
        if (!removed) {
            setFeedback("Task could not be deleted.", false);
            return;
        }

        refreshDashboard();
        if (currentSelection != null && currentSelection.id() == task.id()) {
            if (!tasks.isEmpty()) {
                taskListView.getSelectionModel().selectFirst();
            }
        }
        taskListView.refresh();
        setFeedback("Deleted task " + task.name() + ".", false);
    }

    private void refreshDashboard() {
        List<Task> allTasks = repository.loadTasks();
        tasks.setAll(allTasks);

        Task selectedTask = taskListView.getSelectionModel().getSelectedItem();
        if (selectedTask == null && !allTasks.isEmpty()) {
            selectedTask = allTasks.get(0);
        } else if (selectedTask != null) {
            long selectedTaskId = selectedTask.id();
            selectedTask = allTasks.stream().filter(task -> task.id() == selectedTaskId).findFirst().orElse(null);
        }

        int activeWeekYear = currentWeekYear();
        ProfileSummary profileSummary = repository.loadProfileSummary(activeWeekYear);

        if (selectedTask == null) {
            streakLabel.setText("0 days");
            badgeLabel.setText(String.valueOf(profileSummary.badgeCount()));
            selectedTaskScopeLabel.setText("No task selected.");
            weeklyProgressLabel.setText("Create a task to start your daily stair journey.");
            latestBadgeLabel.setText(profileSummary.recentBadges().isEmpty()
                    ? "No badges earned yet."
                    : profileSummary.recentBadges().get(0).badgeLabel());
            headlineLabel.setText("The climber is waiting for the first task.");
            badgeSectionLabel.setText("All Task Badges");
            milestoneTitleLabel.setText("Current milestone will appear here.");
            milestoneDescriptionLabel.setText("");
            milestoneTitleField.clear();
            milestoneDescriptionArea.clear();
            recentCompletions.clear();
            renderBadges(profileSummary.recentBadges());
            stairGraphic.renderDateWindow(List.of(), Set.of(), -1, -1);
            return;
        }

        if (taskListView.getSelectionModel().getSelectedItem() == null
                || taskListView.getSelectionModel().getSelectedItem().id() != selectedTask.id()) {
            taskListView.getSelectionModel().select(selectedTask);
        }

        renderSelectedTask(selectedTask);
    }

    private void renderSelectedTask(Task selectedTask) {
        List<DailyCompletion> completions = repository.loadDailyCompletions(selectedTask.id());
        List<ProfileBadge> taskBadges = repository.loadProfileSummary(currentWeekYear()).recentBadges().stream()
                .filter(badge -> badge.taskId() == selectedTask.id())
                .toList();
        List<WeeklyLevel> levels = repository.loadLevels(selectedTask.id(), currentWeekYear());
        LocalDate today = LocalDate.now();
        LocalDate rawTaskStartDate = selectedTask.startDate();
        LocalDate taskStartDate = rawTaskStartDate.isAfter(today) ? today : rawTaskStartDate;
        long daysSinceStart = Math.max(0, ChronoUnit.DAYS.between(taskStartDate, today));
        long weekIndex = daysSinceStart / 7;
        LocalDate periodStart = taskStartDate.plusDays(weekIndex * 7);
        // Always show the full current period; include yesterday if it was in the previous period
        LocalDate yesterday = today.minusDays(1);
        LocalDate rawWindowStart = yesterday.isBefore(periodStart) ? yesterday : periodStart;
        LocalDate windowStart = rawWindowStart.isBefore(taskStartDate) ? taskStartDate : rawWindowStart;

        List<DailyCompletion> visibleCompletions = completions.stream()
                .filter(completion -> !completion.completionDate().isBefore(windowStart))
                .sorted(Comparator.comparing(DailyCompletion::completionDate).reversed())
                .toList();
        recentCompletions.setAll(visibleCompletions);

        Set<LocalDate> completedDates = completions.stream()
                .map(DailyCompletion::completionDate)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean todayDone = completedDates.contains(today);
        streakLabel.setText(calculateStreak(completedDates) + " days");
        badgeLabel.setText(String.valueOf(taskBadges.size()));
        selectedTaskScopeLabel.setText(selectedTask.name() + "  ·  started " + DATE_FORMAT.format(taskStartDate));
        latestBadgeLabel.setText(taskBadges.isEmpty()
                ? "No badges earned yet."
                : "Latest badge: " + taskBadges.get(0).badgeLabel());
        headlineLabel.setText(todayDone
                ? "The climber reached today's stair for " + selectedTask.name() + "."
                : "The climber is waiting for today's completion on " + selectedTask.name() + ".");

        weeklyProgressLabel.setText(buildWeeklyProgressText(selectedTask, completedDates));
        badgeSectionLabel.setText("Badges For " + selectedTask.name());
        renderBadges(taskBadges);
        populateCurrentMilestone(selectedTask, levels);

        List<LocalDate> stairDates = new ArrayList<>();
        Set<Integer> completedIndexes = new LinkedHashSet<>();
        int activeIndex = -1;
        int currentIndex = -1;
        for (int i = 0; i < WINDOW_DAYS; i++) {
            LocalDate date = windowStart.plusDays(i);
            stairDates.add(date);
            if (!date.isAfter(today) && completedDates.contains(date)) {
                completedIndexes.add(i);
                activeIndex = i;
            }
            if (date.equals(today)) {
                currentIndex = i;
            }
        }

        int climberIndex = activeIndex; // stand on the last completed step only
        stairGraphic.renderDateWindow(stairDates, completedIndexes, climberIndex, currentIndex);
    }

    private String buildWeeklyProgressText(Task task, Set<LocalDate> completedDates) {
        LocalDate today = LocalDate.now();
        LocalDate taskStart = task.startDate();
        long daysSinceStart = Math.max(0, ChronoUnit.DAYS.between(taskStart, today));
        long weekIndex = daysSinceStart / 7;
        int periodNumber = (int) Math.min(weekIndex + 1, 52);
        LocalDate periodStart = taskStart.plusDays(weekIndex * 7);
        LocalDate periodEnd = periodStart.plusDays(6);

        long periodCompletedCount = completedDates.stream()
                .filter(date -> !date.isBefore(periodStart) && !date.isAfter(periodEnd))
                .count();

        WeeklyLevel currentLevel = repository.loadLevels(task.id(), currentWeekYear()).stream()
                .filter(level -> level.weekNumber() == periodNumber)
                .findFirst()
                .orElse(null);

        if (currentLevel != null && currentLevel.completed()) {
            return "Period " + periodNumber + " complete — 7/7 days. Badge earned!";
        }
        return "Period " + periodNumber + ": " + periodCompletedCount + "/7 days — badge unlocks at 7/7.";
    }

    private void populateCurrentMilestone(Task task, List<WeeklyLevel> levels) {
        LocalDate taskStart = task.startDate();
        long daysSinceStart = Math.max(0, ChronoUnit.DAYS.between(taskStart, LocalDate.now()));
        int periodNumber = (int) Math.min(daysSinceStart / 7 + 1, 52);
        WeeklyLevel currentLevel = levels.stream()
                .filter(level -> level.weekNumber() == periodNumber)
                .findFirst()
                .orElse(null);

        if (currentLevel == null) {
            milestoneTitleLabel.setText("Current milestone not available.");
            milestoneDescriptionLabel.setText("");
            milestoneTitleField.clear();
            milestoneDescriptionArea.clear();
            return;
        }

        milestoneTitleLabel.setText(currentLevel.title());
        milestoneDescriptionLabel.setText(currentLevel.description());
        milestoneTitleField.setText(currentLevel.title());
        milestoneDescriptionArea.setText(currentLevel.description());
    }

    private void showHistoryDialog(Task task) {
        VBox dialogContent = new VBox(16);
        dialogContent.setPadding(new Insets(12));
        dialogContent.setPrefWidth(560);

        buildHistoryContent(task, dialogContent);

        ScrollPane scroll = new ScrollPane(dialogContent);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(480);
        scroll.getStyleClass().add("app-scroll");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("History — " + task.name());
        dialog.setHeaderText("Click any past day to mark / unmark it");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(820);
        dialog.showAndWait();

        refreshDashboard();
        selectTask(task.id());
    }

    private void buildHistoryContent(Task task, VBox container) {
        List<DailyCompletion> completions = repository.loadDailyCompletions(task.id());
        Map<LocalDate, DailyCompletion> byDate = completions.stream()
                .collect(Collectors.toMap(DailyCompletion::completionDate, c -> c));

        LocalDate taskStart = task.startDate();
        LocalDate today = LocalDate.now();
        long daysSinceStart = Math.max(0, ChronoUnit.DAYS.between(taskStart, today));
        int periodsToShow = (int) Math.min(daysSinceStart / 7 + 2, 52);

        List<WeeklyLevel> levels = repository.loadLevels(task.id(), currentWeekYear());
        Map<Integer, WeeklyLevel> levelsByPeriod = levels.stream()
                .collect(Collectors.toMap(WeeklyLevel::weekNumber, l -> l));
        long earnedCount = levels.stream().filter(WeeklyLevel::completed).count();

        Label stats = new Label(
                completions.size() + " days completed  ·  " + earnedCount + " badge" + (earnedCount == 1 ? "" : "s") + " earned  ·  since " + DATE_FORMAT.format(taskStart));
        stats.getStyleClass().add("muted-copy");
        stats.setWrapText(true);

        HBox legend = new HBox(16,
                historyLegendItem(Color.web("#f4845f"), "Done"),
                historyLegendItem(Color.rgb(200, 193, 185), "Missed"),
                historyLegendItem(Color.rgb(235, 230, 225), "Future"));
        legend.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(6);

        for (int d = 1; d <= 7; d++) {
            Label h = new Label("Day " + d);
            h.getStyleClass().add("subtle-copy");
            h.setPrefWidth(46);
            h.setAlignment(Pos.CENTER);
            grid.add(h, d, 0);
        }
        Label badgeHeader = new Label("Badge");
        badgeHeader.getStyleClass().add("subtle-copy");
        grid.add(badgeHeader, 8, 0);

        DateTimeFormatter cellFmt = DateTimeFormatter.ofPattern("d MMM");
        DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("d MMM yy");

        for (int p = 0; p < periodsToShow; p++) {
            int periodNum = p + 1;
            long doneInPeriod = 0;
            for (int d = 0; d < 7; d++) {
                if (byDate.containsKey(taskStart.plusDays((long) p * 7 + d))) doneInPeriod++;
            }
            boolean earned = doneInPeriod >= 7;
            WeeklyLevel level = levelsByPeriod.get(periodNum);

            // Period label: shows name + progress/earned date below
            VBox pBox = new VBox(2);
            Label pName = new Label("P" + periodNum);
            pName.getStyleClass().add(earned ? "badge-earned-label" : "subtle-copy");
            pBox.getChildren().add(pName);
            if (earned && level != null && level.completedAt() != null) {
                Label earnedOn = new Label(level.completedAt().format(shortFmt));
                earnedOn.setStyle("-fx-font-size: 10px; -fx-text-fill: #25653d;");
                pBox.getChildren().add(earnedOn);
            } else if (!earned && doneInPeriod > 0) {
                Label progress = new Label(doneInPeriod + "/7");
                progress.setStyle("-fx-font-size: 10px; -fx-text-fill: #7b8490;");
                pBox.getChildren().add(progress);
            }
            pBox.setPrefWidth(68);
            grid.add(pBox, 0, p + 1);

            // Day cells
            for (int d = 0; d < 7; d++) {
                LocalDate date = taskStart.plusDays((long) p * 7 + d);
                boolean future = date.isAfter(today);
                boolean done = byDate.containsKey(date);
                boolean isToday = date.equals(today);

                Rectangle rect = new Rectangle(44, 40);
                rect.setArcWidth(10);
                rect.setArcHeight(10);
                rect.setFill(future ? Color.rgb(235, 230, 225)
                        : done ? Color.web("#f4845f") : Color.rgb(200, 193, 185));
                if (isToday) {
                    rect.setStroke(Color.web("#102542"));
                    rect.setStrokeWidth(2.5);
                }

                Label dayLabel = new Label(date.format(cellFmt));
                dayLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: "
                        + (done ? "white" : future ? "#c8bfb6" : "#5f6c7b") + ";");

                StackPane cell = new StackPane(rect, dayLabel);
                cell.setPrefSize(46, 42);

                String tip = DATE_FORMAT.format(date) + (done ? " — Done" : future ? " — Future" : " — Missed");
                DailyCompletion dc = byDate.get(date);
                if (dc != null && dc.note() != null && !dc.note().isBlank()) {
                    tip += "\nNote: " + dc.note();
                }
                Tooltip.install(cell, new Tooltip(tip));

                if (!future) {
                    cell.setCursor(javafx.scene.Cursor.HAND);
                    final LocalDate clickDate = date;
                    cell.setOnMouseClicked(e -> {
                        repository.toggleDailyCompletion(task.id(), task.name(), clickDate, taskStart, null);
                        buildHistoryContent(task, container);
                    });
                }

                grid.add(cell, d + 1, p + 1);
            }

            // Badge chip column — only for earned periods
            if (earned && level != null) {
                String chipText = level.title() == null || level.title().isBlank()
                        ? "Week " + periodNum + " Badge"
                        : level.title();
                Label chip = new Label(chipText);
                chip.getStyleClass().add("badge-earned-chip");
                chip.setWrapText(false);

                String tipText = task.name() + " — Consistency Milestone Week " + periodNum;
                if (level.description() != null && !level.description().isBlank()) {
                    tipText += "\n" + level.description();
                }
                if (level.completedAt() != null) {
                    tipText += "\nEarned: " + level.completedAt().format(DATE_FORMAT);
                }
                Tooltip.install(chip, new Tooltip(tipText));

                VBox badgeBox = new VBox(2, chip);
                if (level.completedAt() != null) {
                    Label earnedLabel = new Label("Earned " + level.completedAt().format(shortFmt));
                    earnedLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #25653d;");
                    badgeBox.getChildren().add(earnedLabel);
                }
                grid.add(badgeBox, 8, p + 1);
            }
        }

        container.getChildren().setAll(stats, legend, grid);
    }

    private HBox historyLegendItem(Color color, String label) {
        Rectangle rect = new Rectangle(14, 14);
        rect.setArcWidth(4);
        rect.setArcHeight(4);
        rect.setFill(color);
        Label text = new Label(label);
        text.getStyleClass().add("subtle-copy");
        HBox item = new HBox(6, rect, text);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void saveCurrentMilestoneOverride() {
        Task selectedTask = taskListView.getSelectionModel().getSelectedItem();
        if (selectedTask == null) {
            setFeedback("Select a task first.", false);
            return;
        }

        int year = currentWeekYear();
        LocalDate taskStart = selectedTask.startDate();
        long daysSinceStart = Math.max(0, ChronoUnit.DAYS.between(taskStart, LocalDate.now()));
        int week = (int) Math.min(daysSinceStart / 7 + 1, 52);
        boolean updated = repository.updateWeeklyLevelDetails(
                selectedTask.id(),
                year,
                week,
                milestoneTitleField.getText(),
                milestoneDescriptionArea.getText()
        );

        if (!updated) {
            setFeedback("Current milestone could not be updated.", false);
            return;
        }

        renderSelectedTask(selectedTask);
        setFeedback("Saved milestone details for period " + week + ".", true);
    }

    private int calculateStreak(Set<LocalDate> completedDates) {
        int streak = 0;
        LocalDate cursor = LocalDate.now();
        while (completedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private void renderBadges(List<ProfileBadge> badges) {
        badgePane.getChildren().clear();
        if (badges.isEmpty()) {
            Label empty = new Label("No badges earned yet.");
            empty.getStyleClass().add("muted-copy");
            badgePane.getChildren().add(empty);
            return;
        }

        badges.forEach(badge -> {
            Label badgePill = new Label(badge.badgeLabel());
            badgePill.getStyleClass().add("profile-badge");
            badgePane.getChildren().add(badgePill);
        });
    }

    private void setFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        if (success) {
            if (!feedbackLabel.getStyleClass().contains("success-copy")) {
                feedbackLabel.getStyleClass().add("success-copy");
            }
        } else {
            feedbackLabel.getStyleClass().remove("success-copy");
        }
    }

    private void selectTask(long taskId) {
        for (Task task : tasks) {
            if (task.id() == taskId) {
                taskListView.getSelectionModel().select(task);
                return;
            }
        }
    }

    private int currentWeekYear() {
        return LocalDate.now().get(WeekFields.ISO.weekBasedYear());
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static final class TaskCell extends ListCell<Task> {
        private final java.util.function.Consumer<Task> onDelete;
        private final java.util.function.Consumer<Task> onViewHistory;

        private TaskCell(java.util.function.Consumer<Task> onDelete,
                         java.util.function.Consumer<Task> onViewHistory) {
            this.onDelete = onDelete;
            this.onViewHistory = onViewHistory;
        }

        @Override
        protected void updateItem(Task item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label name = new Label(item.name());
            name.getStyleClass().add("task-name");

            LocalDate created = item.startDate();
            LocalDate safeCreated = created.isAfter(LocalDate.now()) ? LocalDate.now() : created;
            Label since = new Label("since " + DATE_FORMAT.format(safeCreated));
            since.getStyleClass().add("subtle-copy");

            Button historyButton = new Button("History");
            historyButton.getStyleClass().add("link-button");
            historyButton.setOnAction(e -> onViewHistory.accept(item));

            Button deleteButton = new Button("×");
            deleteButton.getStyleClass().add("task-delete-btn");
            deleteButton.setOnAction(e -> onDelete.accept(item));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, name, since, spacer, historyButton, deleteButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("task-row");
            if (getListView() != null
                    && getListView().getSelectionModel().getSelectedItem() != null
                    && getListView().getSelectionModel().getSelectedItem().id() == item.id()) {
                row.getStyleClass().add("task-row-selected");
            }
            setGraphic(row);
        }
    }

    private static final class DailyCompletionCell extends ListCell<DailyCompletion> {
        private static final DateTimeFormatter COMPACT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM");

        @Override
        protected void updateItem(DailyCompletion item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label icon = new Label(item.completionDate().format(COMPACT_DATE_FORMAT));
            icon.getStyleClass().add("activity-chip");
            icon.getStyleClass().add("compact-activity-chip");

            String notePreview = item.note() == null || item.note().isBlank()
                    ? "No notes"
                    : item.note().trim();
            if (notePreview.length() > 34) {
                notePreview = notePreview.substring(0, 31) + "...";
            }

            Label title = new Label(notePreview);
            title.getStyleClass().add("compact-activity-title");
            if (item.note() == null || item.note().isBlank()) {
                title.getStyleClass().add("compact-activity-empty-note");
            }

            Label meta = new Label(TIME_FORMAT.format(item.completedAt()));
            meta.getStyleClass().add("compact-activity-meta");

            Label status = new Label("Done");
            status.getStyleClass().add("badge-earned-label");
            status.getStyleClass().add("compact-earned-label");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            VBox textGroup = new VBox(1, title, meta);
            HBox row = new HBox(10, icon, textGroup, spacer, status);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("activity-row");
            row.getStyleClass().add("compact-activity-row");
            setGraphic(row);
        }
    }
}

