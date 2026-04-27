package com.stairprogress;

import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class StairGraphic extends Pane {
    public static final int STEP_COUNT = 8;

    private static final double STEP_WIDTH = 58;
    private static final double STEP_HEIGHT = 28;
    private static final double BASE_X = 26;
    private static final double BASE_Y = 308;
    private static final double CLIMBER_OFFSET_X = 14;
    private static final double CLIMBER_OFFSET_Y = 12;
    private static final DateTimeFormatter STEP_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM");

    private final List<Rectangle> steps = new ArrayList<>();
    private final List<Label> dateLabels = new ArrayList<>();
    private final Group climber;
    private Consumer<LocalDate> onStepClicked;

    public StairGraphic() {
        setPrefSize(540, 340);
        setMinHeight(340);
        getStyleClass().add("stair-canvas");

        Rectangle glow = new Rectangle(510, 220);
        glow.setArcWidth(42);
        glow.setArcHeight(42);
        glow.setLayoutX(16);
        glow.setLayoutY(72);
        glow.setFill(Color.rgb(244, 124, 73, 0.12));

        getChildren().add(glow);
        buildStairs();
        climber = buildClimber();
        getChildren().add(climber);
        moveClimber(-1, false);
    }

    public void setOnStepClicked(Consumer<LocalDate> onStepClicked) {
        this.onStepClicked = onStepClicked;
    }

    public void render(LocalDate startDate, Set<LocalDate> completedDates, int activeIndex) {
        for (int i = 0; i < STEP_COUNT; i++) {
            LocalDate date = startDate.plusDays(i);
            Rectangle step = steps.get(i);
            Label label = dateLabels.get(i);

            boolean isToday = date.equals(LocalDate.now());
            boolean completed = completedDates.contains(date);

            step.getStyleClass().removeAll("stair-step-complete", "stair-step-pending", "stair-step-today");
            step.getStyleClass().add(completed ? "stair-step-complete" : "stair-step-pending");
            if (isToday) {
                step.getStyleClass().add("stair-step-today");
            }

            label.setText(STEP_DATE_FORMAT.format(date));
            label.getStyleClass().removeAll("stair-date-complete", "stair-date-pending", "stair-date-today");
            label.getStyleClass().add(completed ? "stair-date-complete" : "stair-date-pending");
            if (isToday) {
                label.getStyleClass().add("stair-date-today");
            }

            if (isToday) {
                step.setOnMouseClicked(event -> handleTodayStepClick());
            } else {
                step.setOnMouseClicked(null);
            }
        }

        moveClimber(activeIndex, true);
    }

    public void renderWeekWindow(List<String> labels, Set<Integer> completedIndexes, int activeIndex, int currentIndex) {
        List<String> safeLabels = labels == null ? Collections.emptyList() : labels;
        for (int i = 0; i < STEP_COUNT; i++) {
            Rectangle step = steps.get(i);
            Label label = dateLabels.get(i);

            boolean isCurrent = i == currentIndex;
            boolean completed = completedIndexes.contains(i);

            step.getStyleClass().removeAll("stair-step-complete", "stair-step-pending", "stair-step-today");
            step.getStyleClass().add(completed ? "stair-step-complete" : "stair-step-pending");
            if (isCurrent) {
                step.getStyleClass().add("stair-step-today");
            }

            label.setText(i < safeLabels.size() ? safeLabels.get(i) : "");
            label.getStyleClass().removeAll("stair-date-complete", "stair-date-pending", "stair-date-today");
            label.getStyleClass().add(completed ? "stair-date-complete" : "stair-date-pending");
            if (isCurrent) {
                label.getStyleClass().add("stair-date-today");
            }

            if (isCurrent) {
                step.setOnMouseClicked(event -> handleTodayStepClick());
            } else {
                step.setOnMouseClicked(null);
            }
        }

        moveClimber(activeIndex, true);
    }

    public void renderDateWindow(List<LocalDate> dates, Set<Integer> completedIndexes, int activeIndex, int currentIndex) {
        List<LocalDate> safeDates = dates == null ? Collections.emptyList() : dates;
        for (int i = 0; i < STEP_COUNT; i++) {
            Rectangle step = steps.get(i);
            Label label = dateLabels.get(i);

            LocalDate date = i < safeDates.size() ? safeDates.get(i) : null;
            boolean isCurrent = i == currentIndex && date != null;
            boolean isYesterday = i == currentIndex - 1 && date != null;
            boolean completed = completedIndexes.contains(i);

            step.getStyleClass().removeAll("stair-step-complete", "stair-step-pending", "stair-step-today", "stair-step-yesterday");
            step.getStyleClass().add(completed ? "stair-step-complete" : "stair-step-pending");
            if (isCurrent) step.getStyleClass().add("stair-step-today");
            if (isYesterday) step.getStyleClass().add("stair-step-yesterday");

            label.setText(date == null ? "" : STEP_DATE_FORMAT.format(date));
            label.getStyleClass().removeAll("stair-date-complete", "stair-date-pending", "stair-date-today");
            label.getStyleClass().add(completed ? "stair-date-complete" : "stair-date-pending");
            if (isCurrent) label.getStyleClass().add("stair-date-today");

            if (isCurrent || isYesterday) {
                final LocalDate clickDate = date;
                step.setOnMouseClicked(event -> { if (onStepClicked != null) onStepClicked.accept(clickDate); });
            } else {
                step.setOnMouseClicked(null);
            }
        }

        moveClimber(activeIndex, true);
    }

    private void buildStairs() {
        for (int i = 0; i < STEP_COUNT; i++) {
            double x = BASE_X + i * STEP_WIDTH;
            double y = BASE_Y - (i + 1) * STEP_HEIGHT;

            Rectangle riser = new Rectangle(STEP_WIDTH - 3, STEP_HEIGHT);
            riser.setLayoutX(x);
            riser.setLayoutY(y);
            riser.setArcWidth(18);
            riser.setArcHeight(18);
            riser.getStyleClass().add("stair-step");
            steps.add(riser);
            getChildren().add(riser);

            Label dateLabel = new Label();
            dateLabel.getStyleClass().add("stair-date");
            dateLabel.setAlignment(Pos.CENTER);
            dateLabel.setPrefWidth(STEP_WIDTH - 6);
            dateLabel.setLayoutX(x + 2);
            dateLabel.setLayoutY(y + 4);
            dateLabel.setMouseTransparent(true);
            dateLabels.add(dateLabel);
            getChildren().add(dateLabel);
        }
    }

    private void handleTodayStepClick() {
        if (onStepClicked != null) onStepClicked.accept(LocalDate.now());
    }

    private Group buildClimber() {
        Circle head = new Circle(14, Color.web("#102542"));
        head.setCenterX(0);
        head.setCenterY(-36);

        Line body = createLimb(0, -22, 0, 12, 8);
        Line leftArm = createLimb(0, -10, -18, 0, 6);
        Line rightArm = createLimb(0, -8, 18, -20, 6);
        Line leftLeg = createLimb(0, 12, -15, 34, 7);
        Line rightLeg = createLimb(0, 12, 20, 8, 7);

        Group group = new Group(head, body, leftArm, rightArm, leftLeg, rightLeg);
        group.setLayoutX(BASE_X + CLIMBER_OFFSET_X);
        group.setLayoutY(BASE_Y - CLIMBER_OFFSET_Y);
        return group;
    }

    private Line createLimb(double startX, double startY, double endX, double endY, double width) {
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(Color.web("#102542"));
        line.setStrokeWidth(width);
        line.setSmooth(true);
        line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        return line;
    }

    private void moveClimber(int stepIndex, boolean animate) {
        double targetX = stepIndex < 0
                ? BASE_X + CLIMBER_OFFSET_X
                : BASE_X + CLIMBER_OFFSET_X + (stepIndex * STEP_WIDTH);
        double targetY = stepIndex < 0
                ? BASE_Y - CLIMBER_OFFSET_Y
                : BASE_Y - CLIMBER_OFFSET_Y - ((stepIndex + 1) * STEP_HEIGHT);

        if (!animate) {
            climber.setLayoutX(targetX);
            climber.setLayoutY(targetY);
            return;
        }

        TranslateTransition slide = new TranslateTransition(Duration.millis(520), climber);
        slide.setToX(targetX - climber.getLayoutX());
        slide.setToY(targetY - climber.getLayoutY());
        slide.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition bounce = new ScaleTransition(Duration.millis(520), climber);
        bounce.setFromX(1.0);
        bounce.setFromY(1.0);
        bounce.setToX(1.08);
        bounce.setToY(1.08);
        bounce.setAutoReverse(true);
        bounce.setCycleCount(2);

        ParallelTransition transition = new ParallelTransition(slide, bounce);
        transition.setOnFinished(event -> {
            climber.setLayoutX(targetX);
            climber.setLayoutY(targetY);
            climber.setTranslateX(0);
            climber.setTranslateY(0);
            climber.setScaleX(1.0);
            climber.setScaleY(1.0);
        });
        transition.play();
    }
}
