package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class TranslationController {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final AffineTransform3D global = new AffineTransform3D();

	private final AffineTransform3D displayTransform = new AffineTransform3D();

	private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

	private final AffineTransform3D globalInit = new AffineTransform3D();

	private final AffineTransform3D displayTransformInit = new AffineTransform3D();

	private final AffineTransform3D globalToViewerTransformInit = new AffineTransform3D();

	private final GlobalTransformManager manager;

	private final TransformTracker globalTransformTracker;

	private final TransformTracker displayTransformTracker;

	private final TransformTracker globalToViewerTransformTracker;

	private final AffineTransformWithListeners displayTransformUpdater;

	private final AffineTransformWithListeners globalToViewerTransformUpdater;

	private final double[] delta = new double[3];

	private final AffineTransform3D tmp = new AffineTransform3D();

	public TranslationController(
			final GlobalTransformManager manager,
			final AffineTransformWithListeners displayTransformUpdater,
			final AffineTransformWithListeners globalToViewerTransformUpdater) {

		this.manager = manager;
		this.displayTransformUpdater = displayTransformUpdater; //scale
		this.globalToViewerTransformUpdater = globalToViewerTransformUpdater; //translation
		this.globalTransformTracker = new TransformTracker(global, manager);
		this.displayTransformTracker = new TransformTracker(displayTransform, manager);
		this.globalToViewerTransformTracker = new TransformTracker(globalToViewerTransform, manager);
		this.listenOnTransformChanges();
	}

	public void init() {

		synchronized (manager) {
			globalInit.set(global);
			displayTransformInit.set(displayTransform);
			globalToViewerTransformInit.set(globalToViewerTransform);
		}
	}

	public void translate(final double dX, final double dY) {

		translate(dX, dY, 0.0);
	}

	public void translate(final double dX, final double dY, final double dZ) {
		translate(dX, dY, dZ, null);
	}

	public void translate(final double dX, final double dY, final double dZ, final Duration duration) {

		synchronized (manager) {
			tmp.set(globalInit);
			final double scale = displayTransformInit.get(0, 0);
			delta[0] = dX / scale;
			delta[1] = dY / scale;
			delta[2] = dZ / scale;

			LOG.debug("Delta in screen space: {}", delta);

			LOG.debug(
					"Applying inverse={} of {} to delta",
					globalToViewerTransformInit.inverse(),
					globalToViewerTransformInit
			);
			globalToViewerTransformInit.applyInverse(delta, delta);

			LOG.debug("Delta in global space: {}", delta);

			tmp.set(tmp.get(0, 3) + delta[0], 0, 3);
			tmp.set(tmp.get(1, 3) + delta[1], 1, 3);
			tmp.set(tmp.get(2, 3) + delta[2], 2, 3);

			manager.setTransform(tmp, duration != null ? duration : Duration.ZERO);
		}

	}

	public void listenOnTransformChanges() {

		this.manager.addListener(this.globalTransformTracker);
		this.displayTransformUpdater.addListener(this.displayTransformTracker);
		this.globalToViewerTransformUpdater.addListener(this.globalToViewerTransformTracker);
	}

	public void stopListeningOnTransformChanges() {

		this.manager.removeListener(this.globalTransformTracker);
		this.displayTransformUpdater.removeListener(this.displayTransformTracker);
		this.globalToViewerTransformUpdater.removeListener(this.globalToViewerTransformTracker);
	}

	private static final class TransformTracker implements TransformListener<AffineTransform3D> {

		private final AffineTransform3D transform;

		private final Object lock;

		public TransformTracker(final AffineTransform3D transform, final Object lock) {

			super();
			this.transform = transform;
			this.lock = lock;
		}

		@Override
		public void transformChanged(final AffineTransform3D t) {

			synchronized (lock) {
				transform.set(t);
			}
		}
	}

}
