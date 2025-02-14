package org.janelia.saalfeldlab.paintera.ui.source.state

import bdv.viewer.Source
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.DoubleExpression
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.fx.TextFields
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.CloseButton
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Consumer

class StatePane(
    private val state: SourceState<*, *>,
    private val sourceInfo: SourceInfo,
    activeSourceRadioButtonGroup: ToggleGroup,
    remove: Consumer<Source<*>>,
    width: DoubleExpression,
) {

    private val nameProperty = state.nameProperty()

    private val isCurrentSourceProperty = sourceInfo.isCurrentSource(state.dataSource)
    val isCurrentSource: Boolean by isCurrentSourceProperty.nonnullVal()

    private val statePaneVisibleProperty = state.isVisibleProperty
    var statePaneIsVisible: Boolean by statePaneVisibleProperty.nonnull()

    val pane = TitledPane(null, state.preferencePaneNode()).apply {
        HBox.setHgrow(this, Priority.ALWAYS)
        VBox.setVgrow(this, Priority.ALWAYS)
        isExpanded = false
        alignment = Pos.CENTER_RIGHT
        LOG.debug("_pane width is {} ({})", this.width, width)
    }

    init {
        val closeButton = Button(null, CloseButton.createFontAwesome(2.0)).apply {
            onAction = EventHandler { remove.accept(state.dataSource) }
            tooltip = Tooltip("Remove source")
        }
        val activeSource = RadioButton().apply {
            tooltip = Tooltip("Select as active source")
            selectedProperty().addListener { _, _, new -> if (new) sourceInfo.currentSourceProperty().set(state.dataSource) }
            isCurrentSourceProperty.addListener { _, _, newv -> if (newv) isSelected = true }
            isSelected = isCurrentSource
            toggleGroup = activeSourceRadioButtonGroup
        }
        val visibilityIconViewVisible = FontAwesome[FontAwesomeIcon.EYE, 2.0].apply { stroke = Color.BLACK }
        val visibilityIconViewInvisible = FontAwesome[FontAwesomeIcon.EYE_SLASH, 2.0].apply {
            stroke = Color.GRAY
            fill = Color.GRAY
        }

        val visibilityButton = Button(null).apply {
            onAction = EventHandler { statePaneIsVisible = !statePaneIsVisible }
            graphicProperty().bind(statePaneVisibleProperty.createNonNullValueBinding { if (it) visibilityIconViewVisible else visibilityIconViewInvisible })
            maxWidth = 20.0
            tooltip = Tooltip("Toggle visibility")
        }
        val nameField = TextFields.editableOnDoubleClick().apply {
            textProperty().bindBidirectional(nameProperty)
            tooltip = Tooltip().also {
                it.textProperty().bind(nameProperty.createNullableValueBinding { name -> "Source $name: Double click to change name, enter to confirm, escape to discard." })
            }
            backgroundProperty().bind(editableProperty().createNonNullValueBinding { if (it) EDITABLE_BACKGROUND else UNEDITABLE_BACKGROUND })
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        val titleBox = HBox(
            nameField,
            NamedNode.bufferNode(),
            activeSource,
            visibilityButton,
            closeButton
        ).apply {
            alignment = Pos.CENTER
            padding = Insets(0.0, RIGHT_PADDING, 0.0, LEFT_PADDING)
        }
        with(TitledPaneExtensions) {
            pane.graphicsOnly(titleBox)
        }
        // TODO how to get underlined in TextField?
//        nameField.underlineProperty().bind(_isCurrentSource)

    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private const val LEFT_PADDING = 0.0

        private const val RIGHT_PADDING = 0.0

        private val EDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets(-1.4, 0.0, 1.0, 2.0)))

        private val UNEDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5), CornerRadii.EMPTY, Insets.EMPTY))

    }

}
