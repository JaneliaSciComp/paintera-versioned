package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.export.ProgressWriter
import bdv.ij.util.ProgressWriterIJ
import bdv.viewer.Source
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.scene.control.ButtonType
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.nameIt
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.scicomp.v5.fs.V5FSWriter
import org.janelia.scicomp.v5.lib.uri.V5FSURL
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles


class CloneVersionedDataset(private val currentSource: Source<*>?, vararg allSources: SourceState<*, *>) {

    private val allSources = allSources.toList()

    private val username = TextField().apply { maxWidth = Double.MAX_VALUE }
    private val indexPathField: DirectoryField = DirectoryField(System.getProperty("user.home"), 100.0)
    private val datastorePathField: DirectoryField = DirectoryField(System.getProperty("user.home"), 100.0)
    private val localPathField : DirectoryField = DirectoryField(System.getProperty("user.home"), 100.0)

    private val pane = VBox(
        nameIt("Username:", NAME_WIDTH, true, username),
        nameIt("Versioned Index Path:", NAME_WIDTH, true, indexPathField.asNode()),
        nameIt("Data path:", NAME_WIDTH, true, datastorePathField.asNode()),
        nameIt("Local path:", NAME_WIDTH, true, localPathField.asNode())
    )

    fun showDialog(projectDirectory: String?): String? {
//        localPath.directoryProperty().value = Path.of(projectDirectory!!).toFile()
        var path : String?= null
        PainteraAlerts.confirmation("C_lone", "_Cancel", true).apply {
            headerText = "Clone project"
            dialogPane.content = pane



            dialogPane.lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION) { e: ActionEvent ->
                val username = username.text
                val indexPath = indexPathField.directoryProperty().value!!.absolutePath
                val dataPath = datastorePathField.directoryProperty().value!!.absolutePath
                val localPath = localPathField.directoryProperty().value!!.absolutePath
                    LOG.debug("Trying to clone project index: `{}' data:`{}' to `{}' ", indexPath,dataPath,localPath)
                    if (indexPath.isNullOrEmpty()) throw IOException("Index Path not specified!")
                    if (dataPath.isNullOrEmpty()) throw IOException("Data Path not specified!")
                    if (localPath.isNullOrEmpty()) throw IOException("Local Path not specified!")
                    if (username.isNullOrEmpty()) throw IOException("Username not specified!")
                    val progress = ReadOnlyDoubleWrapper(this, "progress", 0.0)
                    val valueNotNullProperty = SimpleBooleanProperty(false)

                    val task = Tasks.createTask<String> {
                        paintera.baseView.disabledPropertyBindings.put(it, valueNotNullProperty.not())
                        var result = ""
                        try {
                            val writer = V5FSWriter.cloneFrom(V5FSWriter(indexPath,dataPath),localPath,username)
                           result = writer.versionedUrl
                        }catch (ex:Exception){
                            LOG.error("Unable to create empty dataset", ex)
                            e.consume()
                            exceptionAlert(Constants.NAME, "Unable to create new dataset: ${ex.message}", ex).show()
                        }
                       result
                    }.onEnd { it
                        paintera.baseView.disabledPropertyBindings.remove(it)
                    }


                    InvokeOnJavaFXApplicationThread.invoke(Runnable {
                        progress.set(0.1)
                        task.valueProperty().addListener { obs, old, new ->
                            valueNotNullProperty.set(new != null)
                        }
                    })

                path = task.submit().value
//                   V5FSURL(localPath,dataPath).url
            }
        }.showAndWait()
        return path
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private const val NAME_WIDTH = 150.0
    }
}
