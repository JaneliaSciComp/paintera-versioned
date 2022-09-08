package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.viewer.Source
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.Pair
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.bufferNode
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.nameIt
import org.janelia.saalfeldlab.fx.ui.ObjectField.Companion.stringField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField.Companion.doubleField
import org.janelia.saalfeldlab.fx.ui.SpatialField.Companion.intField
import org.janelia.saalfeldlab.fx.ui.SpatialField.Companion.longField
import org.janelia.saalfeldlab.n5.N5TreeNode
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceMetadata
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.scicomp.v5.V5URI
import org.janelia.scicomp.v5.VersionedN5Writer
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.net.URI
import java.nio.file.Path
import java.util.*


class CreateVersionedDataset(private val currentSource: Source<*>?, vararg allSources: SourceState<*, *>) {

    private val allSources = allSources.toList()
    private val mipmapLevels = FXCollections.observableArrayList<MipMapLevel>()
    private val mipmapLevelsNode by lazy {
        MipMapLevel.makeNode(
            mipmapLevels, 100.0,
            NAME_WIDTH, 30.0,
            *SubmitOn.values()
        )
    }

    private val populateFromCurrentSource = MenuItem("_From Current Source").apply {
        onAction = EventHandler {
            it.consume()
            populateFrom(currentSource)
        }
    }


    private val populateFromSource = Menu("_Select Source").apply {
        isVisible = allSources.isNotEmpty()
        allSources.forEach { source ->
            items += MenuItem(source.nameProperty().get()).apply {
                onAction = EventHandler { populateFrom(source.dataSource) }
                isMnemonicParsing = false
            }
        }
    }


    private val setFromButton = MenuButton("_Populate", null, populateFromSource, populateFromCurrentSource)
    private val setFromCurrentBox =
        HBox(bufferNode(), setFromButton).apply { HBox.setHgrow(children[0], Priority.ALWAYS) }
    private val username = TextField().apply { maxWidth = Double.MAX_VALUE }
    private val name = TextField().apply { maxWidth = Double.MAX_VALUE }

    private val versionedIndexField: DirectoryField = DirectoryField(System.getProperty("user.home"), 100.0)
    private val dataStoreField: DirectoryField = DirectoryField(System.getProperty("user.home"), 100.0)
    private val dataset = stringField("", *SubmitOn.values()).apply {
        valueProperty().addListener { _, _, newv: String? ->
            if (newv != null && name.text.isNullOrEmpty()) {
                val split = newv.split("/").toTypedArray()
                name.text = split[split.size - 1]
            }
        }
    }


    private val dimensions = longField(1, { it > 0 }, 100.0, *SubmitOn.values())
    private val blockSize = intField(1, { it > 0 }, 100.0, *SubmitOn.values())
    private val resolution = doubleField(1.0, { it > 0 }, 100.0, *SubmitOn.values())
    private val offset = doubleField(0.0, { true }, 100.0, *SubmitOn.values())
    private val scaleLevels = TitledPane("Scale Levels", mipmapLevelsNode)
    private val pane = VBox(
        nameIt("username", NAME_WIDTH, true, username),
        nameIt("Name", NAME_WIDTH, true, name),
        nameIt("Versioned Index:", NAME_WIDTH, true, versionedIndexField.asNode()),
        nameIt("Data Store:", NAME_WIDTH, true, dataStoreField.asNode()),
        nameIt("Dataset", NAME_WIDTH, true, dataset.textField),
        nameIt("Dimensions", NAME_WIDTH, false, bufferNode(), dimensions.node),
        nameIt("Block Size", NAME_WIDTH, false, bufferNode(), blockSize.node),
        nameIt("Resolution", NAME_WIDTH, false, bufferNode(), resolution.node),
        nameIt("Offset", NAME_WIDTH, false, bufferNode(), offset.node),
        setFromCurrentBox,
        scaleLevels
    )

    init {
        currentSource?.let { populateFrom(it) }
    }

    fun showDialog(projectDirectory: String?): Optional<Pair<MetadataState, String>> {
        val metadataStateProp = SimpleObjectProperty<MetadataState>()
        versionedIndexField.directoryProperty().value = Path.of(projectDirectory!! ).toFile()
        dataStoreField.directoryProperty().value = Path.of(projectDirectory!!).toFile()
        PainteraAlerts.confirmation("C_reate", "_Cancel", true).apply {
            headerText = "Create new Label dataset"
            dialogPane.content = pane
            dialogPane.lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION) { e: ActionEvent ->

                val versionedIndex = Path.of(versionedIndexField.directoryProperty().value!!.absolutePath,"versionedIndex").toFile().absolutePath
                val datastore = Path.of(dataStoreField.directoryProperty().value!!.absolutePath,"datastore").toFile().absolutePath
                val dataset = dataset.value
                val name = name.text
                val username = username.text
                val uri = V5URI.format(versionedIndex, datastore)
                try {
                    LOG.warn("Trying to create empty label dataset `{}' in container `{}'", dataset, uri)
                    if (dataset.isNullOrEmpty()) throw IOException("Dataset not specified!")
                    if (name.isNullOrEmpty()) throw IOException("Name not specified!")
                    if (username.isNullOrEmpty()) throw IOException("Username not specified!")

                    N5Data.createEmptyVersionedLabelDataset(
                        URI(uri),
                        dataset,
                        dimensions.asLongArray(),
                        blockSize.asIntArray(),
                        resolution.asDoubleArray(),
                        offset.asDoubleArray(),
                        mipmapLevels.stream().map { it.downsamplingFactors() }.toList().toTypedArray(),
                        mipmapLevels.stream().mapToInt { it.maxNumEntries() }.toArray(), false
                    ) as VersionedN5Writer


//                    val writer: VersionedN5Writer = n5.openDataset(dataset)
//                    val pathToDataset = Path.of(datastore, dataset).toFile().canonicalPath

                    val writer = n5Factory.openWriter(uri) as VersionedN5Writer
                    writer.setCurrentDataset(dataset)
                    writer.setUserID(username)

                    writer.commit()


                    N5Helpers.parseMetadata(writer).ifPresent { tree: N5TreeNode ->
                        val metadata = tree.metadata
                        val containerState = N5ContainerState(uri, writer, writer)
                        createMetadataState(containerState, metadata).ifPresent {
                            metadataStateProp.set(it) }
                    }
                } catch (ex: Exception) {
                    LOG.error("Unable to create empty dataset", ex)
                    e.consume()
                    exceptionAlert(Constants.NAME, "Unable to create new dataset: ${ex.message}", ex).show()
                }
            }
        }.showAndWait()
        val name = name.text
        val result =  Optional.ofNullable(metadataStateProp.get())
            .map { metadataState: MetadataState -> Pair(metadataState, name) }
        return result
    }


    private fun populateFrom(source: Source<*>?) {
        source?.let {
            setMipMapLevels(source)
            if (source is N5DataSourceMetadata<*, *>) {
                val metadataState = source.metadataState
                val container = metadataState.n5ContainerState.url
                try {
                    val v5uri = V5URI(URI(container))
                    versionedIndexField.directoryProperty().value = File(v5uri.versionedIndexPath)
                    dataStoreField.directoryProperty().value = File(v5uri.dataStorePath)
                }catch (e:Exception){

                }

            }
            val data = source.getSource(0, 0)
            dimensions.x.value = data.dimension(0)
            dimensions.y.value = data.dimension(1)
            dimensions.z.value = data.dimension(2)
            if (data is AbstractCellImg<*, *, *, *>) {
                val grid = data.cellGrid
                blockSize.x.value = grid.cellDimension(0)
                blockSize.y.value = grid.cellDimension(1)
                blockSize.z.value = grid.cellDimension(2)
            }
            val transform = AffineTransform3D()
            source.getSourceTransform(0, 0, transform)
            resolution.x.value = transform[0, 0]
            resolution.y.value = transform[1, 1]
            resolution.z.value = transform[2, 2]
            offset.x.value = transform[0, 3]
            offset.y.value = transform[1, 3]
            offset.z.value = transform[2, 3]
        }
    }

    private fun setMipMapLevels(source: Source<*>) {
        mipmapLevels.clear()
        val firstTransform = AffineTransform3D()
        source.getSourceTransform(0, 0, firstTransform)
        var previousFactors = doubleArrayOf(firstTransform[0, 0], firstTransform[1, 1], firstTransform[2, 2])
        for (i in 1 until source.numMipmapLevels) {
            val transform = AffineTransform3D()
            source.getSourceTransform(0, i, transform)
            val downsamplingFactors = doubleArrayOf(transform[0, 0], transform[1, 1], transform[2, 2])
            val level = MipMapLevel(2, -1, 100.0, 150.0, *SubmitOn.values())
            level.relativeDownsamplingFactors.x.value = downsamplingFactors[0] / previousFactors[0]
            level.relativeDownsamplingFactors.y.value = downsamplingFactors[1] / previousFactors[1]
            level.relativeDownsamplingFactors.z.value = downsamplingFactors[2] / previousFactors[2]
            previousFactors = downsamplingFactors
            mipmapLevels.add(level)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private const val NAME_WIDTH = 150.0
    }
}
