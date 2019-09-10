package org.janelia.saalfeldlab.fx.event;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.janelia.saalfeldlab.fx.util.OnWindowInitListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class KeyTracker implements InstallAndRemove<Scene>
{

	private final HashSet<KeyCode> activeKeys = new HashSet<>();

	private final ActivateKey activate = new ActivateKey();

	private final DeactivateKey deactivate = new DeactivateKey();

	private final OnFocusChanged onFocusChanged = new OnFocusChanged();

	public void installInto(final Window window) {
		installInto(window.getScene(), window);
	}

	public void installInto(final Scene scene, final Window window) {
		scene.addEventFilter(KeyEvent.KEY_RELEASED, deactivate);
		scene.addEventFilter(KeyEvent.KEY_PRESSED, activate);
		window.focusedProperty().addListener(onFocusChanged);
	}

	public void removeFrom(final Scene scene, final Window window) {
		scene.removeEventFilter(KeyEvent.KEY_RELEASED, deactivate);
		scene.removeEventFilter(KeyEvent.KEY_PRESSED, activate);
		window.focusedProperty().removeListener(onFocusChanged);
	}

	@Override
	public void installInto(final Scene scene)
	{
		scene.addEventFilter(KeyEvent.KEY_RELEASED, deactivate);
		scene.addEventFilter(KeyEvent.KEY_PRESSED, activate);
		scene.windowProperty().addListener(new OnWindowInitListener(window -> window.focusedProperty().addListener(onFocusChanged)));
	}

	@Override
	public void removeFrom(final Scene scene)
	{
		scene.removeEventFilter(KeyEvent.KEY_PRESSED, activate);
		scene.removeEventFilter(KeyEvent.KEY_RELEASED, deactivate);
		if (scene.getWindow() != null)
			scene.getWindow().focusedProperty().removeListener(onFocusChanged);
	}

	private final class ActivateKey implements EventHandler<KeyEvent>
	{
		@Override
		public void handle(final KeyEvent event)
		{
			synchronized (activeKeys)
			{
				activeKeys.add(event.getCode());
			}
		}
	}

	private final class DeactivateKey implements EventHandler<KeyEvent>
	{
		@Override
		public void handle(final KeyEvent event)
		{
			synchronized (activeKeys)
			{
				activeKeys.remove(event.getCode());
			}
		}
	}

	private final class OnFocusChanged implements ChangeListener<Boolean>
	{

		@Override
		public void changed(final ObservableValue<? extends Boolean> observable, final Boolean oldValue, final Boolean
				newValue)
		{
			if (!newValue)
				synchronized (activeKeys)
				{
					activeKeys.clear();
				}
		}

	}

	public boolean areOnlyTheseKeysDown(final KeyCode... codes)
	{
		final HashSet<KeyCode> codesHashSet = new HashSet<>(Arrays.asList(codes));
		synchronized (activeKeys)
		{
			return codesHashSet.equals(activeKeys);
		}
	}

	public boolean areKeysDown(final KeyCode... codes)
	{
		synchronized (activeKeys)
		{
			return activeKeys.containsAll(Arrays.asList(codes));
		}
	}

	public int activeKeyCount()
	{
		synchronized (activeKeys)
		{
			return activeKeys.size();
		}
	}

	public boolean noKeysActive()
	{
		return activeKeyCount() == 0;
	}

	public List<KeyCode> getActiveKeyCodes(final boolean includeModifiers) {
		return activeKeys
				.stream()
				.filter(k -> includeModifiers || !k.isModifierKey())
				.collect(Collectors.toList());
	}

}
