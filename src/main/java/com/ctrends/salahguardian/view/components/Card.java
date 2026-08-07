package com.ctrends.salahguardian.view.components;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The rounded, elevated surface every dashboard section sits on.
 *
 * <p>Providing it as a component rather than repeating the same {@code VBox}
 * plus style class in each view keeps the Material inspired look consistent and
 * gives the stylesheets a single hook, {@code .card}, to target.</p>
 *
 * @author CTrends Software
 */
public class Card extends VBox {

    private final Label titleLabel = new Label();
    private final VBox content = new VBox();

    /**
     * Creates a titled card.
     *
     * @param title heading shown above the content, may be empty
     */
    public Card(String title) {
        getStyleClass().add("card");
        setSpacing(12);
        setPadding(new Insets(18));

        titleLabel.getStyleClass().add("card-title");
        titleLabel.setText(title == null ? "" : title.toUpperCase());
        titleLabel.setVisible(title != null && !title.isBlank());
        titleLabel.setManaged(titleLabel.isVisible());

        content.getStyleClass().add("card-content");
        content.setSpacing(8);
        VBox.setVgrow(content, Priority.ALWAYS);

        getChildren().addAll(titleLabel, content);
    }

    /**
     * Adds nodes to the card body.
     *
     * @param nodes the children to append
     * @return {@code this}, for chaining
     */
    public Card add(Node... nodes) {
        content.getChildren().addAll(nodes);
        return this;
    }

    /**
     * @return the card's body container, for advanced layout needs
     */
    public VBox content() {
        return content;
    }

    /**
     * Marks this card as the visually dominant one on the screen.
     *
     * @return {@code this}, for chaining
     */
    public Card asAccent() {
        getStyleClass().add("card-accent");
        return this;
    }

    /**
     * Makes the card expand to fill the width available in an {@code HBox}.
     *
     * @return {@code this}, for chaining
     */
    public Card growHorizontally() {
        setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(this, Priority.ALWAYS);
        return this;
    }
}
