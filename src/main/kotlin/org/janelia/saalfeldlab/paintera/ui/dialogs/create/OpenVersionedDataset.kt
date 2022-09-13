package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import javafx.event.ActionEvent
import javafx.scene.control.ButtonType
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.nameIt
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.scicomp.v5.lib.uri.V5FSURL
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.nio.file.Path

class OpenVersionedDataset {

    private val indexPathField: DirectoryField = DirectoryField("", 100.0)
    private val datastorePathField: DirectoryField = DirectoryField("", 100.0)
    private val pane = VBox(
        nameIt("Index Path", NAME_WIDTH, true, indexPathField.asNode()),
        nameIt("Datastore path", NAME_WIDTH, true, datastorePathField.asNode()),
    )

    fun showDialog(projectDirectory: String?): String {
        var uri = ""
        indexPathField.directoryProperty().value = Path.of(projectDirectory!!).toFile()
        datastorePathField.directoryProperty().value = Path.of(projectDirectory!!).toFile()
        PainteraAlerts.confirmation("O_pen", "_Cancel", true).apply {
            headerText = "Open versioned dataset"
            dialogPane.content = pane
            dialogPane.lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION) { e: ActionEvent ->
                val indexPath = indexPathField.directoryProperty().value!!.absolutePath
                val datastorePath= datastorePathField.directoryProperty().value!!.absolutePath

                uri = V5FSURL(indexPath,datastorePath).url
                try {
                    if (indexPath.isNullOrEmpty()) throw IOException("Index Path not specified!")
                    if (datastorePath.isNullOrEmpty()) throw IOException("Datastore Path not specified!")
                    if ( uri == "") throw IOException("Invalid URI!")

                } catch (ex: IOException) {
                    LOG.error("Unable to create empty dataset", ex)
                    e.consume()
                    exceptionAlert(Constants.NAME, "Unable to create new dataset: ${ex.message}", ex).show()
                }
            }
        }.showAndWait()
        return uri
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private const val NAME_WIDTH = 150.0
    }
}
