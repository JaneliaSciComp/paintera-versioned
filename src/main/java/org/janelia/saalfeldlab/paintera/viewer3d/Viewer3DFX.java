package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.util.fx.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

public class Viewer3DFX extends Pane
{

	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Group root;

	private final Group meshesGroup;

	private final SubScene scene;

	private final PerspectiveCamera camera;

	private final Group cameraGroup;

	private final AmbientLight lightAmbient = new AmbientLight(new Color(0.1, 0.1, 0.1, 1));

	private final PointLight lightSpot = new PointLight(new Color(1.0, 0.95, 0.85, 1));

	private final PointLight lightFill = new PointLight(new Color(0.35, 0.35, 0.65, 1));

	private final Scene3DHandler handler;

	private final Group3DCoordinateTracker coordinateTracker;

	private final Transform cameraTransform = new Translate(0, 0, -1);

	private final ViewFrustum viewFrustum;

	private final BooleanProperty isMeshesEnabled = new SimpleBooleanProperty();

	private final BooleanProperty showBlockBoundaries = new SimpleBooleanProperty();

	private final IntegerProperty rendererBlockSize = new SimpleIntegerProperty();

	public Viewer3DFX(final double width, final double height)
	{
		super();
		this.root = new Group();
		this.meshesGroup = new Group();
		this.coordinateTracker = new Group3DCoordinateTracker(meshesGroup);
		this.setWidth(width);
		this.setHeight(height);
		this.scene = new SubScene(root, width, height, true, SceneAntialiasing.BALANCED);
		this.scene.setFill(Color.BLACK);

		this.camera = new PerspectiveCamera(true);
		this.camera.setNearClip(0.01);
		this.camera.setFarClip(10.0);
		this.camera.setTranslateY(0);
		this.camera.setTranslateX(0);
		this.camera.setTranslateZ(0);
		this.camera.setFieldOfView(90);
		this.scene.setCamera(this.camera);
		this.cameraGroup = new Group();

		this.getChildren().add(this.scene);
		this.root.getChildren().addAll(cameraGroup, meshesGroup);
		this.scene.widthProperty().bind(widthProperty());
		this.scene.heightProperty().bind(heightProperty());
		lightSpot.setTranslateX(-10);
		lightSpot.setTranslateY(-10);
		lightSpot.setTranslateZ(-10);
		lightFill.setTranslateX(10);

		this.cameraGroup.getChildren().addAll(camera, lightAmbient, lightSpot, lightFill);
		this.cameraGroup.getTransforms().add(cameraTransform);

		this.handler = new Scene3DHandler(this);

		this.root.visibleProperty().bind(this.isMeshesEnabled);

		final ObjectProperty<AffineTransform3D> sceneTransformProperty = new SimpleObjectProperty<>();
		this.handler.addListener(obs -> sceneTransformProperty.set(Transforms.fromTransformFX(this.handler.getAffine())));
		this.viewFrustum = new ViewFrustum(this.camera, Transforms.fromTransformFX(this.cameraTransform), sceneTransformProperty);

		final InvalidationListener sizeChangedListener = obs -> viewFrustum.update(getWidth(), getHeight());
		widthProperty().addListener(sizeChangedListener);
		heightProperty().addListener(sizeChangedListener);
	}

	public void setInitialTransformToInterval(final Interval interval)
	{
		handler.setInitialTransformToInterval(interval);
	}

	public Scene3DHandler sceneHandler()
	{
		return handler;
	}

	public SubScene scene()
	{
		return scene;
	}

	public Group root()
	{
		return root;
	}

	public Group meshesGroup()
	{
		return meshesGroup;
	}

	public Group cameraGroup()
	{
		return cameraGroup;
	}

	public Group3DCoordinateTracker coordinateTracker()
	{
		return this.coordinateTracker;
	}

	public ViewFrustum viewFrustum()
	{
		return this.viewFrustum;
	}

	public BooleanProperty isMeshesEnabledProperty()
	{
		return this.isMeshesEnabled;
	}

	public BooleanProperty showBlockBoundariesProperty()
	{
		return this.showBlockBoundaries;
	}

	public IntegerProperty rendererBlockSizeProperty()
	{
		return this.rendererBlockSize;
	}
}
