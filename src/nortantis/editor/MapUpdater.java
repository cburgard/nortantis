package nortantis.editor;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.SwingWorker;

import nortantis.CancelledException;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.graph.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.swing.MapEdits;
import nortantis.swing.SwingHelper;
import nortantis.swing.UpdateType;
import nortantis.util.Logger;
import nortantis.util.Tuple2;

public abstract class MapUpdater
{
	private boolean isMapBeingDrawn;
	private ReentrantLock drawLock;
	private ReentrantLock interactionsLock;
	public MapParts mapParts;
	private boolean createEditsIfNotPresentAndUseMapParts;
	private Dimension maxMapSize;
	private boolean enabled;
	private boolean isMapReadyForInteractions;
	private Queue<Runnable> tasksToRunWhenMapReady;
	private ArrayDeque<MapUpdate> updatesToDraw;
	private MapCreator currentNonIncrementalMapCreator;

	/**
	 * 
	 * @param createEditsIfNotPresentAndUseMapParts
	 *            When true, drawing the map for the first time will fill in
	 *            MapSettings.Edits, and a MapParts object will be used. Only
	 *            set this to false if the map will only do full re-draws.
	 */
	public MapUpdater(boolean createEditsIfNotPresentAndUseMapParts)
	{
		drawLock = new ReentrantLock();
		interactionsLock = new ReentrantLock();
		this.createEditsIfNotPresentAndUseMapParts = createEditsIfNotPresentAndUseMapParts;
		tasksToRunWhenMapReady = new ConcurrentLinkedQueue<>();
		updatesToDraw = new ArrayDeque<>();
	}

	/**
	 * Redraws the entire map, then displays it.
	 */
	public void createAndShowMapFull()
	{
		createAndShowMap(UpdateType.Full, null, null, null, null);
	}

	public void createAndShowMapTextChange()
	{
		createAndShowMapTextChange(null);
	}

	public void createAndShowMapTextChange(Runnable callback)
	{
		List<Runnable> callbacks = new ArrayList<>();
		if (callback != null)
		{
			callbacks.add(callback);
		}
		createAndShowMap(UpdateType.Text, null, null, null, callbacks);
	}

	public void createAndShowMapFontsChange()
	{
		createAndShowMap(UpdateType.Fonts, null, null, null, null);
	}

	public void createAndShowMapTerrainChange()
	{
		createAndShowMap(UpdateType.Terrain, null, null, null, null);
	}

	public void createAndShowMapGrungeOrFrayedEdgeChange()
	{
		createAndShowMap(UpdateType.GrungeAndFray, null, null, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null, null, null);
	}

	public void createAndShowMapIncrementalUsingEdges(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged, null, null);
	}
	
	public void reprocessBooks()
	{
		createAndShowMap(UpdateType.ReprocessBooks, null, null, null, null);
	}

	/**
	 * Redraws the map based on a change that was made.
	 * 
	 * For incremental drawing, this compares the edits in the change with the
	 * current state of the edits from getEdits() to determine what changed.
	 * 
	 * @param change
	 *            The 'before' state. Used to determine what needs to be
	 *            redrawn.
	 * @param preRun Code to run in the foreground thread before drawing this change.
	 */
	public void createAndShowMapFromChange(MapChange change)
	{
		List<Runnable> preRuns = new ArrayList<>();
		if (change.preRun != null)
		{
			preRuns.add(change.preRun);
		}
		
		if (change.updateType != UpdateType.Incremental)
		{
			createAndShowMap(change.updateType, null, null, preRuns, null);
		}
		else
		{
			Set<Center> centersChanged = getCentersWithChangesInEdits(change.settings.edits);
			Set<Edge> edgesChanged = null;
			// Currently createAndShowMap doesn't support drawing both center
			// edits and edge edits at the same time, so there is no
			// need to find edges changed if centers were changed.
			if (centersChanged.size() == 0)
			{
				edgesChanged = getEdgesWithChangesInEdits(change.settings.edits);
			}
			createAndShowMap(UpdateType.Incremental, centersChanged, edgesChanged, preRuns, null);
		}
	}

	private Set<Center> getCentersWithChangesInEdits(MapEdits changeEdits)
	{
		Set<Center> changedCenters = getEdits().centerEdits.stream()
				.filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index)))
				.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());

		Set<RegionEdit> regionChanges = getEdits().regionEdits.values().stream()
				.filter(rEdit -> !rEdit.equals(changeEdits.regionEdits.get(rEdit.regionId))).collect(Collectors.toSet());
		for (RegionEdit rEdit : regionChanges)
		{
			Set<Center> regionCenterEdits = changeEdits.centerEdits.stream()
					.filter(cEdit -> cEdit.regionId != null && cEdit.regionId == rEdit.regionId)
					.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());
			changedCenters.addAll(regionCenterEdits);
		}

		return changedCenters;
	}

	private Set<Edge> getEdgesWithChangesInEdits(MapEdits changeEdits)
	{
		return getEdits().edgeEdits.stream().filter(eEdit -> !eEdit.equals(changeEdits.edgeEdits.get(eEdit.index)))
				.map(eEdit -> mapParts.graph.edges.get(eEdit.index)).collect(Collectors.toSet());
	}

	/**
	 * Clears values from mapParts as needed to trigger those parts to re-redraw
	 * based on what type of update we're making.
	 * 
	 * @param updateType
	 */
	private void clearMapPartsAsNeeded(UpdateType updateType)
	{
		if (mapParts == null)
		{
			return;
		}

		if (updateType == UpdateType.Full)
		{
			if (mapParts != null)
			{
				mapParts = new MapParts();
			}
		}
		else if (updateType == UpdateType.Incremental)
		{

		}
		else if (updateType == UpdateType.Text)
		{

		}
		else if (updateType == UpdateType.Fonts)
		{
			mapParts.textDrawer = null;
		}
		else if (updateType == UpdateType.Terrain)
		{
			mapParts.mapBeforeAddingText = null;
		}
		else if (updateType == UpdateType.GrungeAndFray)
		{
			mapParts.frayedBorderBlur = null;
			mapParts.frayedBorderColor = null;
			mapParts.frayedBorderMask = null;
			mapParts.grunge = null;
		}
		else if (updateType == UpdateType.ReprocessBooks)
		{
			
		}
		else
		{
			throw new IllegalStateException("Unrecognized update type: " + updateType);
		}

	}

	/**
	 * Creates a new set that has the most up-to-date version of the centers in
	 * the given set. This is necessary because incremental and full redraws can
	 * run out of order, and as a result, a full redraw might recreate the
	 * graph, while an incremental change is waiting to run, causing
	 * centersChanged (passed to createAndShowMap) to hold Center objects no
	 * longer in the graph, and so they could be out of date.
	 */
	private Set<Center> getCurrentCenters(Set<Center> centers)
	{
		if (centers == null)
		{
			return null;
		}
		return centers.stream().map(c -> mapParts.graph.centers.get(c.index)).collect(Collectors.toSet());
	}

	/**
	 * Like getCurrentCenters but for edges.
	 */
	private Set<Edge> getCurrentEdges(Set<Edge> edges)
	{
		if (edges == null)
		{
			return null;
		}
		return edges.stream().map(e -> mapParts.graph.edges.get(e.index)).collect(Collectors.toSet());
	}
	
	private boolean isUpdateTypeThatAllowsInteractions(UpdateType updateType)
	{
		return updateType == UpdateType.Incremental || updateType == UpdateType.Text || updateType == UpdateType.ReprocessBooks;
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<Runnable> preRuns, List<Runnable> callbacks)
	{
		if (!enabled)
		{
			return;
		}

		if (isMapBeingDrawn)
		{
			updatesToDraw.add(new MapUpdate(updateType, centersChanged, edgesChanged, preRuns, callbacks));
			return;
		}

		isMapBeingDrawn = true;
		if (!isUpdateTypeThatAllowsInteractions(updateType))
		{
			isMapReadyForInteractions = false;
		}
		
		if (updateType != UpdateType.ReprocessBooks)
		{
			onBeginDraw();
		}

		final MapSettings settings = getSettingsFromGUI();

		if (createEditsIfNotPresentAndUseMapParts && settings.edits.isEmpty())
		{
			settings.edits.bakeGeneratedTextAsEdits = true;
		}
		
		if (preRuns != null)
		{
			for (Runnable runnable : preRuns)
			{
				runnable.run();
			}
		}

		SwingWorker<Tuple2<BufferedImage, Rectangle>, Void> worker = new SwingWorker<Tuple2<BufferedImage, Rectangle>, Void>()
		{
			@Override
			public Tuple2<BufferedImage, Rectangle> doInBackground() throws IOException, CancelledException
			{
				if (!isUpdateTypeThatAllowsInteractions(updateType))
				{
					Logger.clear();
					interactionsLock.lock();
				}
				drawLock.lock();
				try
				{
					clearMapPartsAsNeeded(updateType);

					if (updateType == UpdateType.Incremental)
					{
						BufferedImage map = getCurrentMapForIncrementalUpdate();
						// Incremental update
						if (centersChanged != null && centersChanged.size() > 0)
						{
							Rectangle replaceBounds = new MapCreator().incrementalUpdateCenters(settings, mapParts, map,
									getCurrentCenters(centersChanged));
							return new Tuple2<>(map, replaceBounds);
						}
						else if (edgesChanged != null && edgesChanged.size() > 0)
						{
							Rectangle replaceBounds = new MapCreator().incrementalUpdateEdges(settings, mapParts, map,
									getCurrentEdges(edgesChanged));
							return new Tuple2<>(map, replaceBounds);
						}
						else
						{
							// Nothing to do.
							return new Tuple2<>(map, null);
						}
					}
					else if (updateType == UpdateType.ReprocessBooks)
					{
						if (mapParts != null && mapParts.textDrawer != null)
						{
							mapParts.textDrawer.processBooks(settings.books);
						}
						return new Tuple2<>(null, null);
					}
					else
					{
						if (maxMapSize != null && (maxMapSize.width <= 0 || maxMapSize.height <= 0))
						{
							return null;
						}

						if (mapParts == null && createEditsIfNotPresentAndUseMapParts)
						{
							mapParts = new MapParts();
						}

						BufferedImage map;
						try
						{
							currentNonIncrementalMapCreator = new MapCreator();
							map = currentNonIncrementalMapCreator.createMap(settings, maxMapSize, mapParts);
						}
						catch (CancelledException e)
						{
							Logger.println("Map creation cancelled.");
							return new Tuple2<>(null, null);
						}

						System.gc();
						return new Tuple2<>(map, null);
					}
				}
				finally
				{
					drawLock.unlock();
					if (!isUpdateTypeThatAllowsInteractions(updateType))
					{
						interactionsLock.unlock();
					}
				}
			}

			@Override
			public void done()
			{
				BufferedImage map = null;
				Rectangle replaceBounds = null;
				try
				{
					Tuple2<BufferedImage, Rectangle> tuple = get();
					if (tuple != null)
					{
						map = tuple.getFirst();
						replaceBounds = tuple.getSecond();
					}
				}
				catch (InterruptedException ex)
				{
					throw new RuntimeException(ex);
				}
				catch (Exception ex)
				{
					SwingHelper.handleBackgroundThreadException(ex, null, false);
				}

				if (map != null)
				{
					if (createEditsIfNotPresentAndUseMapParts)
					{
						initializeCenterEditsIfEmpty(settings.edits);
						initializeRegionEditsIfEmpty(settings.edits);
						initializeEdgeEditsIfEmpty(settings.edits);
					}
					
					if (mapParts != null)
					{
						mapParts.iconDrawer.removeIconEditsThatFailedToDraw(settings.edits, mapParts.graph);
					}
					
					MapUpdate next = combineAndGetNextUpdateToDraw();

					if (updateType != UpdateType.ReprocessBooks)
					{
						boolean anotherDrawIsQueued = next != null;
						int scaledBorderWidth = settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
						onFinishedDrawing(map, anotherDrawIsQueued, scaledBorderWidth,
								replaceBounds == null ? null
										: new Rectangle(replaceBounds.x + scaledBorderWidth, replaceBounds.y + scaledBorderWidth,
												replaceBounds.width, replaceBounds.height));
					}

					isMapBeingDrawn = false;
					
					if (callbacks != null)
					{
						for (Runnable runnable : callbacks)
						{
							runnable.run();
						}
					}

					if (next != null)
					{
						createAndShowMap(next.updateType, next.centersChanged, next.edgesChanged, next.preRuns, next.callbacks);
					}

					isMapReadyForInteractions = true;
				}
				else
				{
					if (updateType != UpdateType.ReprocessBooks)
					{
						onFailedToDraw();
					}
					isMapBeingDrawn = false;
				}

				while (tasksToRunWhenMapReady.size() > 0)
				{
					tasksToRunWhenMapReady.poll().run();
				}
			}

		};
		worker.execute();
	}

	protected abstract void onBeginDraw();

	protected abstract MapSettings getSettingsFromGUI();

	protected abstract void onFinishedDrawing(BufferedImage map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
			Rectangle incrementalChangeArea);

	protected abstract void onFailedToDraw();

	protected abstract MapEdits getEdits();

	protected abstract BufferedImage getCurrentMapForIncrementalUpdate();

	/**
	 * Combines the updates in updatesToDraw when it makes sense to do so, they
	 * can be drawn together.
	 * 
	 * @return The combined update to draw
	 */
	private MapUpdate combineAndGetNextUpdateToDraw()
	{
		if (updatesToDraw.isEmpty())
		{
			return null;
		}
		
		Optional<MapUpdate> full = updatesToDraw.stream().filter(update -> update.updateType == UpdateType.Full).findFirst();
		if (full.isPresent())
		{
			// There's a full update on the queue. We only need to do that one.
			updatesToDraw.clear();
			return full.get();
		}
		
		// Combine other types updates until we hit one that isn't
		// the same type.
		MapUpdate update = updatesToDraw.poll();
		while (updatesToDraw.size() > 0 && updatesToDraw.peek().updateType == update.updateType)
		{
			update.add(updatesToDraw.poll());
		}
		return update;
	}

	private void initializeCenterEditsIfEmpty(MapEdits edits)
	{
		if (edits.centerEdits.isEmpty())
		{
			edits.initializeCenterEdits(mapParts.graph.centers, mapParts.iconDrawer);
		}
	}

	private void initializeEdgeEditsIfEmpty(MapEdits edits)
	{
		if (edits.edgeEdits.isEmpty())
		{
			edits.initializeEdgeEdits(mapParts.graph.edges);
		}
	}

	private void initializeRegionEditsIfEmpty(MapEdits edits)
	{
		if (edits.regionEdits.isEmpty())
		{
			edits.initializeRegionEdits(mapParts.graph.regions.values());
		}
	}

	public void setMaxMapSize(Dimension dimension)
	{
		maxMapSize = dimension;
	}

	private class MapUpdate
	{
		Set<Center> centersChanged;
		Set<Edge> edgesChanged;
		UpdateType updateType;
		List<Runnable> callbacks;
		List<Runnable> preRuns;

		public MapUpdate(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<Runnable> preRuns, List<Runnable> callbacks)
		{
			this.updateType = updateType;
			if (centersChanged != null)
			{
				this.centersChanged = new HashSet<Center>(centersChanged);
			}
			if (edgesChanged != null)
			{
				this.edgesChanged = new HashSet<Edge>(edgesChanged);
			}
			
			if (callbacks != null)
			{
				this.callbacks = callbacks;				
			}
			else
			{
				this.callbacks = new ArrayList<>();
			}
			
			if (preRuns != null)
			{
				this.preRuns = preRuns;
			}
			else
			{
				this.preRuns = new ArrayList<>();
			}
		}

		public void add(MapUpdate other)
		{
			if (other == null)
			{
				return;
			}

			if (updateType != other.updateType)
			{
				throw new IllegalArgumentException();
			}
			
			preRuns.addAll(other.preRuns);
			callbacks.addAll(other.callbacks);

			if (updateType == UpdateType.Incremental)
			{
				if (centersChanged != null && other.centersChanged != null)
				{
					centersChanged.addAll(other.centersChanged);
				}
				else if (centersChanged == null && other.centersChanged != null)
				{
					centersChanged = new HashSet<>(other.centersChanged);
				}

				if (edgesChanged != null && other.edgesChanged != null)
				{
					edgesChanged.addAll(other.edgesChanged);
				}
				else if (edgesChanged == null && other.edgesChanged != null)
				{
					edgesChanged = new HashSet<>(other.edgesChanged);
				}
			}
		}
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public void doIfMapIsReadyForInteractions(Runnable action)
	{
		doIfMapIsReadyForInteractions(action, true);
	}

	public void doWhenMapIsReadyForInteractions(Runnable action)
	{
		doIfMapIsReadyForInteractions(action, false);
	}

	private void doIfMapIsReadyForInteractions(Runnable action, boolean skipIfLocked)
	{
		// One might wonder why I have both a boolean flag
		// (isMapReadyForInteractions) and a lock (interactionsLock) to prevent
		// user interactions while a map is doing a non-incremental draw. The
		// reason for the flag is to prevent new user interactions
		// after a draw is started and before the draw has finished (since some
		// of the drawing is done in the event dispatch thread
		// after the map finishes drawing in a swing worker thread). The lock is
		// needed to prevent new swing worker threads from starting
		// drawing a map while the event dispatch thread is still handling a
		// user interaction (since doing so could result in something like
		// mapParts.graph being null, which would cause a crash).
		if (isMapReadyForInteractions)
		{
			boolean isLocked = false;
			try
			{
				isLocked = interactionsLock.tryLock(0, TimeUnit.MILLISECONDS);
				if (isLocked)
				{
					action.run();
				}
				else
				{
					if (skipIfLocked)
					{
						return;
					}
					else
					{
						tasksToRunWhenMapReady.add(action);
					}
				}
			}
			catch (InterruptedException e1)
			{
			}
			finally
			{
				if (isLocked)
				{
					interactionsLock.unlock();
				}
			}
		}
		else
		{
			if (!skipIfLocked)
			{
				tasksToRunWhenMapReady.add(action);
			}
		}
	}

	public void dowWhenMapIsNotDrawing(Runnable action)
	{
		if (!isMapBeingDrawn)
		{
			boolean isLocked = false;
			try
			{
				isLocked = drawLock.tryLock(0, TimeUnit.MILLISECONDS);
				if (isLocked)
				{
					action.run();
				}
				else
				{
					tasksToRunWhenMapReady.add(action);
				}
			}
			catch (InterruptedException e1)
			{
			}
			finally
			{
				if (isLocked)
				{
					drawLock.unlock();
				}
			}
		}
		else
		{
			tasksToRunWhenMapReady.add(action);
		}
	}

	public boolean isMapBeingDrawn()
	{
		return isMapBeingDrawn;
	}

	public void cancel()
	{
		if (currentNonIncrementalMapCreator != null && isMapBeingDrawn)
		{
			currentNonIncrementalMapCreator.cancel();
		}
		tasksToRunWhenMapReady.clear();
	}

}
