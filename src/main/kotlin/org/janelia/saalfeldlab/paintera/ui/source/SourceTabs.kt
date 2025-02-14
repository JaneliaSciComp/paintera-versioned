package org.janelia.saalfeldlab.paintera.ui.source

import bdv.viewer.Source
import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.ButtonType
import javafx.scene.control.TitledPane
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Window
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.source.state.StatePane
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.stream.Collectors

typealias OnJFXAppThread = InvokeOnJavaFXApplicationThread

class SourceTabs(private val info: SourceInfo) {

    val widthProperty = SimpleDoubleProperty()

    private val contents = VBox().apply {
        padding = Insets(0.0, 0.0, 0.0, 10.4)
        minWidthProperty().bind(widthProperty)
        maxWidthProperty().bind(widthProperty)
        prefWidthProperty().bind(widthProperty)
        widthProperty().addListener { _, _, new -> LOG.debug("contents width is {} ({})", new, widthProperty) }
    }

    private val statePaneCache = mutableMapOf<Source<*>, StatePane>()

    private val statePanes = FXCollections.observableArrayList<StatePane>().also { p ->
        p.addListener(ListChangeListener {
            OnJFXAppThread { this.contents.children.setAll(p.map { it.pane }) }
        })
    }
    private val activeSourceToggleGroup = ToggleGroup()

    val node: Node
        get() = contents

    init {
        LOG.debug("Constructing {}", SourceTabs::class.java.name)
        this.info.trackSources().addListener(ListChangeListener {
            val copy = ArrayList(this.info.trackSources())
            val show = copy.stream().map { source -> statePaneCache.computeIfAbsent(source) { this.makeStatePane(it) } }.collect(Collectors.toList())
            this.statePanes.setAll(show)
        })

        this.info.removedSourcesTracker().addListener(ListChangeListener { change -> change.list.toTypedArray().forEach { statePaneCache.remove(it) } })

    }

    private fun makeStatePane(source: Source<*>): StatePane {
        val p = StatePane(
            info.getState(source),
            info,
            activeSourceToggleGroup,
            { removeDialog(info, it, node.scene?.window) },
            widthProperty
        )
        addDragAndDropListener(p.pane, info, contents.children)
        return p
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun removeDialog(info: SourceInfo, source: Source<*>, window: Window?) {
            val name = info.getState(source)?.nameProperty()?.get() ?: source.name
            val index = info.indexOf(source)
            val confirmRemoval = PainteraAlerts
                .confirmation("_Remove", "_Cancel", true)
                .also { it.contentText = "Remove source #$index `$name?'" }
            confirmRemoval.headerText = null
            confirmRemoval.initModality(Modality.APPLICATION_MODAL)
            val buttonClicked = confirmRemoval.showAndWait()
            if (buttonClicked.filter { ButtonType.OK == it }.isPresent) {
                try {
                    info.removeSource(source)
                } catch (e: Exception) {
                    Exceptions.exceptionAlert(
                        Constants.NAME,
                        "Unable to remove source #$index `$name': ${e.message}",
                        e, owner = window
                    )
                }
            }
        }

        private fun addDragAndDropListener(p: Node, info: SourceInfo, children: List<Node>) {
            p.setOnDragDetected { p.startFullDrag() }

            p.setOnMouseDragReleased { event ->
                val origin = event.gestureSource
                if (origin !== p && origin is TitledPane) {
                    val sourceIndex = children.indexOf(origin)
                    val targetIndex = children.indexOf(p)
                    info.moveSourceTo(sourceIndex, targetIndex)
                }
            }
        }
    }

}
