package org.janelia.saalfeldlab.paintera.ui.dialogs.create.versioned;

import bdv.viewer.Source;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CloneVersionedDataset;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CloneVersionedDatasetHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView paintera,
			final Supplier<String> projectDirectory) {

		final var owner = Optional.ofNullable(paintera)
				.map(PainteraBaseView::viewer3D)
				.map(Viewer3DFX::scene)
				.map(SubScene::getScene)
				.map(Scene::getWindow).orElse(null);
		createAndAddNewLabelDataset(paintera, projectDirectory, Exceptions.handler(Constants.NAME, "Unable to create new Dataset", null, owner));
	}

	private static void createAndAddNewLabelDataset(
			final PainteraBaseView paintera,
			final Supplier<String> projectDirectory,
			final Consumer<Exception> exceptionHandler) {

		createAndAddNewLabelDataset(
				paintera,
				projectDirectory,
				exceptionHandler,
				paintera.sourceInfo().currentSourceProperty().get(),
				paintera.sourceInfo().trackSources().toArray(Source[]::new));
	}

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projecDirectory,
			final Consumer<Exception> exceptionHandler,
			final Source<?> currentSource,
			final Source<?>... allSources) {

		try {
			createAndAddNewLabelDataset(pbv, projecDirectory, currentSource, allSources);
		} catch (final Exception e) {
			exceptionHandler.accept(e);
		}
	}

	private static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projectDirectory,
			final Source<?> currentSource,
			final Source<?>... allSources) {

		if (!pbv.isActionAllowed(MenuActionType.CreateLabelSource)) {
			LOG.debug("Creating Label Sources is disabled");
			return;
		}

		final CloneVersionedDataset cd = new CloneVersionedDataset(currentSource, Arrays.stream(allSources).map(pbv.sourceInfo()::getState).toArray(SourceState[]::new));
		cd.showDialog(projectDirectory.get());
	}
}
