package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.viewer.Source
import javafx.event.ActionEvent
import javafx.scene.control.ButtonType
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.nameIt
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janus.api.VersionedStorageAPI
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.nio.file.Path


class CloneVersionedDataset(private val currentSource: Source<*>?, vararg allSources: SourceState<*, *>) {

    private val allSources = allSources.toList()

    private val username = TextField().apply { maxWidth = Double.MAX_VALUE }
    private val projectPath: DirectoryField = DirectoryField("", 100.0)
    private val localPath: DirectoryField = DirectoryField("", 100.0)

    private val pane = VBox(
        nameIt("Username", NAME_WIDTH, true, username),
        nameIt("Project path", NAME_WIDTH, true, projectPath.asNode()),
        nameIt("Local path", NAME_WIDTH, true, localPath.asNode())
    )

    fun showDialog(projectDirectory: String?) {
        localPath.directoryProperty().value = Path.of(projectDirectory!!).toFile()
        PainteraAlerts.confirmation("C_lone", "_Cancel", true).apply {
            headerText = "Clone project"
            dialogPane.content = pane
            dialogPane.lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION) { e: ActionEvent ->
                val username = username.text
                val projectPath = projectPath.directoryProperty().value!!.absolutePath
                val localPath = localPath.directoryProperty().value!!.absolutePath

                try {
                    LOG.debug("Trying to clone project `{}'", projectPath)
                    if (projectPath.isNullOrEmpty()) throw IOException("Project Path not specified!")
                    if (localPath.isNullOrEmpty()) throw IOException("Local Path not specified!")
                    if (username.isNullOrEmpty()) throw IOException("Username not specified!")
                    // TODO in thread and waiting gui
                    VersionedStorageAPI.cloneProject(projectPath, localPath, username)
                } catch (ex: IOException) {
                    LOG.error("Unable to create empty dataset", ex)
                    e.consume()
                    exceptionAlert(Constants.NAME, "Unable to create new dataset: ${ex.message}", ex).show()
                }
            }
        }.showAndWait()

        return
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private const val NAME_WIDTH = 150.0
    }
}
