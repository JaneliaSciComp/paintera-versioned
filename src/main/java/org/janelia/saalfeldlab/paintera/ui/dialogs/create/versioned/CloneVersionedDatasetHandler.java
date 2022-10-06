package org.janelia.saalfeldlab.paintera.ui.dialogs.create.versioned;

import bdv.viewer.Source;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CloneVersionedDataset;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5.GenericBackendDialogN5;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5.N5FactoryOpener;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5.N5OpenSourceDialog;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
		String path = cd.showDialog(projectDirectory.get());
		if(path == null)
			return;
		N5FactoryOpener opener = new N5FactoryOpener();
		opener.getSelectionProperty().set(path);
		try (final GenericBackendDialogN5 dialog = opener.backendDialog()) {
			N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
			osDialog.setHeaderFromBackendType("source");
			Optional<GenericBackendDialogN5> optBackend = osDialog.showAndWait();
			if (optBackend.isEmpty())
				return;
			N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, osDialog.getChannelSelection(), pbv, projectDirectory);
			opener.selectionAccepted();
		} catch (Exception e1) {
			LOG.debug("Unable to open dataset", e1);

			Alert alert = Exceptions.exceptionAlert(Constants.NAME, "Unable to open data set", e1);
			alert.initModality(Modality.APPLICATION_MODAL);
			Optional.ofNullable(pbv.getNode().getScene()).map(Scene::getWindow).ifPresent(alert::initOwner);
			alert.show();
		}

	}
}
