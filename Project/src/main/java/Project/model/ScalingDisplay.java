package Project.model;

import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;

/**
 * Custom ScalingDisplay Region implementing advanced glass look for the main calculator output.
 * (Scale pop animation on text change has been disabled for cleaner/snappier UX.)
 * Extracted from GlassCalculator to reduce monolithic main class.
 */
public class ScalingDisplay extends StackPane {
    private final Label valueLabel = new Label("0");

    public ScalingDisplay() {
        valueLabel.setFont(Font.font(34));
        valueLabel.setTextFill(Color.WHITE);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);

        // Layered glass background with inner highlight
        this.setStyle(
            "-fx-background-color: linear-gradient(to bottom, rgba(255,255,255,0.12) 0%, rgba(255,255,255,0.03) 30%, rgba(18,20,30,0.72) 100%);" +
            "-fx-background-radius: 18;" +
            "-fx-border-color: rgba(255,255,255,0.25);" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 18;" +
            "-fx-padding: 12 20;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 20, 0.4, 0, 8), " +
                        "innershadow(gaussian, rgba(255,255,255,0.18), 10, 0.7, 0, 2);"
        );

        this.getChildren().add(valueLabel);
        this.setMinHeight(72);
        StackPane.setAlignment(valueLabel, Pos.CENTER_RIGHT);

        // Maximize GPU usage for smooth display animations
        this.setCache(true);
        this.setCacheHint(CacheHint.SPEED);
    }

    public void setDisplayText(String text) {
        valueLabel.setText(text);
    }

    // Backward compatible setText for existing code
    public void setText(String text) {
        setDisplayText(text);
    }

    public String getText() {
        return valueLabel.getText();
    }
}
