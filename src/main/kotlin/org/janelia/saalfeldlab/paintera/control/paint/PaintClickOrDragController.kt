package org.janelia.saalfeldlab.paintera.control.paint

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.input.MouseEvent
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.LinAlgHelpers
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask.Companion.setNewViewerMask
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse
import org.janelia.saalfeldlab.paintera.exception.PainteraException
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendAndTransformBoundingBox
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.union
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Predicate

private data class Postion(var x: Double = 0.0, var y: Double = 0.0) {

    constructor(mouseEvent: MouseEvent) : this(mouseEvent.x, mouseEvent.y)

    fun update(x: Double, y: Double) {
        this.x = x
        this.y = y
    }

    fun update(mouseEvent: MouseEvent) {
        mouseEvent.apply { update(x, y) }
    }

    private fun toDoubleArray() = doubleArrayOf(x, y)

    override fun toString() = "<Position: ($x, $y)>"

    infix fun linalgSubtract(other: Postion): DoubleArray {
        val thisDoubleArray = toDoubleArray()
        LinAlgHelpers.subtract(thisDoubleArray, other.toDoubleArray(), thisDoubleArray)
        return thisDoubleArray
    }

    operator fun plusAssign(normalizedDragPos: DoubleArray) {
        val xy = this.toDoubleArray() inPlaceAdd normalizedDragPos
        this.x = xy[0]
        this.y = xy[1]
    }

    operator fun minusAssign(normalizedDragPos: DoubleArray) {
        val xy = this.toDoubleArray() inPlaceSubtract normalizedDragPos
        this.x = xy[0]
        this.y = xy[1]
    }

    private infix fun DoubleArray.inPlaceSubtract(other: DoubleArray): DoubleArray {
        LinAlgHelpers.subtract(this, other, this)
        return this
    }

    private infix fun DoubleArray.inPlaceAdd(other: DoubleArray): DoubleArray {
        LinAlgHelpers.add(this, other, this)
        return this
    }
}


class PaintClickOrDragController(
    private val paintera: PainteraBaseView,
    private val viewer: ViewerPanelFX,
    private val paintId: () -> Long,
    private val brushRadius: () -> Double,
    private val brushDepth: () -> Double
) {

    fun submitPaint() {
        synchronized(this) {
            when {
                !isPainting -> LOG.debug("Not currently painting -- will not do anything")
                paintIntoThis == null -> LOG.debug("No current source available -- will not do anything")
                !submitMask -> {
                    LOG.debug("submitMask flag: $submitMask")
                    isPainting = false
                }

                else -> try {
                    with(paintIntoThis!!) {
                        viewerMask?.let { mask ->
                            val sourceInterval = extendAndTransformBoundingBox(maskInterval!!.asRealInterval, mask.initialMaskToSourceWithDepthTransform, .5)
                            val repaintInterval = mask.initialSourceToGlobalTransform.estimateBounds(sourceInterval)
                            applyMask(currentMask, sourceInterval.smallestContainingInterval, FOREGROUND_CHECK)
                            var refreshAfterApplyingMask: ChangeListener<Boolean>? = null
                            refreshAfterApplyingMask = ChangeListener<Boolean> { obs, _, isApplyingMask ->
                                if (!isApplyingMask) {
                                    paintera.orthogonalViews().requestRepaint(repaintInterval)
                                    obs.removeListener(refreshAfterApplyingMask!!)
                                }
                            }
                            isApplyingMaskProperty.addListener(refreshAfterApplyingMask)
                        }
                    }
                } catch (e: Exception) {
                    InvokeOnJavaFXApplicationThread { exceptionAlert(Constants.NAME, "Exception when trying to submit mask.", e).show() }
                } finally {
                    release()
                }
            }
        }
    }

    class IllegalIdForPainting(val id: Long?) : PainteraException("Cannot paint this id: $id")

    @get:Synchronized
    private var isPainting = false

    private var paintIntoThis: MaskedSource<*, *>? = null


    /* In Initial Mask Space */
    internal var maskInterval: Interval? = null
    private val position = Postion()

    var viewerMask: ViewerMask? = null
    private var submitMask = true


    internal fun provideMask(viewerMask: ViewerMask) {
        submitMask = false
        this.viewerMask = viewerMask
    }

    fun isPainting(): Boolean {
        return isPainting
    }

    fun isApplyingMaskProperty(): ReadOnlyBooleanProperty? {
        return paintIntoThis?.isApplyingMaskProperty
    }

    fun getViewerMipMapLevel(): Int {
        (paintera.sourceInfo().currentSourceProperty().get() as MaskedSource<*, *>).let { currentSource ->
            val screenScaleTransform = AffineTransform3D().also {
                viewer.renderUnit.getScreenScaleTransform(0, it)
            }
            return viewer.state.getBestMipMapLevel(screenScaleTransform, currentSource)
        }
    }

    fun startPaint(event: MouseEvent) {
        LOG.debug("Starting New Paint")
        if (isPainting) {
            LOG.debug("Already painting -- will not start new paint.")
            return
        }
        synchronized(this) {
            (paintera.sourceInfo().currentSourceProperty().get() as? MaskedSource<*, *>)?.let { currentSource ->
                try {
                    val screenScaleTransform = AffineTransform3D().also {
                        viewer.renderUnit.getScreenScaleTransform(0, it)
                    }
                    val viewerTransform = AffineTransform3D()
                    val state = viewer.state
                    val level = synchronized(state) {
                        state.getViewerTransform(viewerTransform)
                        state.getBestMipMapLevel(screenScaleTransform, currentSource)
                    }

                    /* keep and set mask on source, or generate new and set  */
                    when {
                        viewerMask == null -> {
                            generateViewerMask(level, currentSource)
                        }

                        currentSource.currentMask == null -> {
                            viewerMask!!.setViewerMaskOnSource()
                        }

                        else -> LOG.trace("Viewer Mask was Provided, but source already has a mask. Doing Nothing. ")
                    }

                    isPainting = true
                    maskInterval = null
                    paintIntoThis = currentSource
                    position.update(event)
                    paint(position)
                } catch (e: MaskInUse) {
                    // Ensure we never enter a painting state when an exception occurs
                    release()
                    InvokeOnJavaFXApplicationThread {
                        if (e.offerReset()) {
                            PainteraAlerts.confirmation("Yes", "No", true, paintera.node.scene.window).apply {
                                headerText = "Unable to paint."

                                contentText = """
                                     The "Busy Mask" alert has displayed at least three times without being cleared. Would you like to force reset the mask?

                                     This may result in loss of some of the most recent uncommitted label annotations. Only do this if you  suspect an error has occured. You may consider waiting a bit to see if the mask releases on it's own.
                                """.trimIndent()
                                (dialogPane.lookupButton(ButtonType.OK) as? Button)?.let {
                                    it.isFocusTraversable = false
                                    it.onAction = EventHandler {
                                        currentSource.resetMasks()
                                    }
                                }

                                show()
                                //NOTE: Normally, the "OK" is focused by default, however since we are always holding down SPACE during paint (at least currently)
                                // Then when we release space, it will trigger the Ok, without the user meaning to, perhaps. Request focus away from the Ok button.
                                dialogPane.requestFocus()
                            }
                        } else {
                            exceptionAlert(Constants.NAME, "Unable to paint.", e).apply {
                                show()
                                //NOTE: Normally, the "OK" is focused by default, however since we are always holding down SPACE during paint (at least currently)
                                // Then when we release space, it will trigger the Ok, without the user meaning to, perhaps. Request focus away from the Ok button.
                                dialogPane.requestFocus()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Ensure we never enter a painting state when an exception occurs
                    release()
                    InvokeOnJavaFXApplicationThread {
                        exceptionAlert(Constants.NAME, "Unable to paint.", e).showAndWait()
                    }
                }
            }
        }
    }

    /*NOTE: If `submitMask` is false, then it is the developers respondsibility to
     * relase the resources from this controller, and to apply the mask MaskedSource when desired,
     * and ultimately reset the MaskedSource's masks */
    fun generateViewerMask(
        level: Int = getViewerMipMapLevel(),
        currentSource: MaskedSource<*, *> = paintera.sourceInfo().currentSourceProperty().get() as MaskedSource<*, *>,
        submitMask: Boolean = true
    ): ViewerMask {

        val id = paintId()
        val maskInfo = MaskInfo(0, level, UnsignedLongType(id))
        return currentSource.setNewViewerMask(maskInfo, viewer, brushDepth()).also {
            viewerMask = it
            this.submitMask = submitMask
        }
    }

    fun extendPaint(event: MouseEvent) {
        if (!isPainting) {
            LOG.debug("Not currently painting -- will not paint")
            return
        }
        synchronized(this) {
            val targetPosition = Postion(event)
            if (targetPosition != position) {
                try {
                    LOG.debug("Drag: paint at screen from $position to $targetPosition")
                    var draggedDistance: Double
                    val normalizedDragPos = (targetPosition linalgSubtract position).also {
                        //NOTE: Calculate distance before noramlizing
                        draggedDistance = LinAlgHelpers.length(it)
                        LinAlgHelpers.normalize(it)
                    }
                    LOG.debug("Number of paintings triggered {}", draggedDistance + 1)
                    repeat(draggedDistance.toInt() + 1) {
                        paint(position)
                        position += normalizedDragPos
                    }
                    LOG.debug("Painting ${draggedDistance + 1} times with radius ${brushRadius()} took a total of {}ms")
                } finally {
                    position.update(event)
                }
            }
        }
    }

    @Synchronized
    private fun paint(pos: Postion) = pos.run { paint(x, y) }

    @Synchronized
    private fun paint(viewerX: Double, viewerY: Double) {
        LOG.trace("At {} {}", viewerX, viewerY)
        when {
            !isPainting -> LOG.debug("Not currently activated for painting, returning without action").also { return }
            viewerMask == null -> LOG.debug("Current mask is null, returning without action").also { return }
        }

        viewerMask?.run {

            val viewerPointToMaskPoint = this.displayPointToInitialMaskPoint(viewerX.toInt(), viewerY.toInt())
            val paintIntervalInMask = Paint2D.paintIntoViewer(
                viewerImg,
                if (submitMask) 1 else paintId(),
                viewerPointToMaskPoint,
                brushRadius() * xScaleChange
            )

            val globalPaintInterval = extendAndTransformBoundingBox(paintIntervalInMask, initialGlobalToMaskTransform.inverse(), .5)
            maskInterval = paintIntervalInMask union maskInterval

            paintera.orthogonalViews().requestRepaint(globalPaintInterval)
        }


    }

    internal fun release() {
        viewerMask = null
        isPainting = false
        maskInterval = null
        paintIntoThis = null
        submitMask = true
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private val FOREGROUND_CHECK = Predicate { t: UnsignedLongType -> Label.isForeground(t.get()) }
    }

}
