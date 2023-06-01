package nortantis.editor;

import java.awt.geom.Area;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import nortantis.IconDrawer;
import nortantis.MapText;
import nortantis.Region;
import nortantis.util.Helper;
import nortantis.util.Range;

/**
 * Stores edits made by a user to a map. These are stored as modifications from the generated content.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapEdits implements Serializable
{
	/**
	 * All fields in this class must either be thread safe or must not be modified after initialization.
	 */
	
	/**
	 * Text the user has edited, added, moved, or rotated. The key is the text id.
	 */
	public CopyOnWriteArrayList<MapText> text;
	public List<CenterEdit> centerEdits;
	public ConcurrentHashMap<Integer, RegionEdit> regionEdits;
	public boolean hasIconEdits;
	public List<EdgeEdit> edgeEdits;
	/**
	 * Not stored. A flat the editor uses to tell TextDrawer to generate text and store it as edits.
	 */
	public boolean bakeGeneratedTextAsEdits;
		
	public MapEdits()
	{
		text = new CopyOnWriteArrayList<>();
		centerEdits = new ArrayList<>();
		regionEdits = new ConcurrentHashMap<>();
		edgeEdits = new ArrayList<>();
	}

	public boolean isEmpty()
	{
		return text.isEmpty() && centerEdits.isEmpty();
	}
	
	public void initializeCenterEdits(List<Center> centers, IconDrawer iconDrawer)
	{
		centerEdits = new ArrayList<>(centers.size());
		for (int index : new Range(centers.size()))
		{
			Center c = centers.get(index);
			centerEdits.add(new CenterEdit(index, c.isWater, c.isLake, c.region != null ? c.region.id : null, null, null));
		}
		
		hasIconEdits = true;
		storeCenterIcons(iconDrawer.centerIcons);
		storeCenterTrees(iconDrawer.trees);
	}
	
	public void initializeEdgeEdits(List<Edge> edges)
	{
		edgeEdits = new ArrayList<>(edges.size());
		for (Edge edge : edges)
		{
			edgeEdits.add(new EdgeEdit(edge.index, edge.river));
		}
	}
	
	public void initializeRegionEdits(Collection<Region> regions)
	{
		for (Region region : regions)
		{
			RegionEdit edit = new RegionEdit(region.id, region.backgroundColor);
			regionEdits.put(edit.regionId, edit);
		}
	}

	/**
	 * Stores icons generated by IconDrawer into map edits so they can be saved and edited.
	 * @param centerIcons Icons generated by IconDrawer
	 */
	private void storeCenterIcons(Map<Integer, CenterIcon> centerIcons)
	{
		for (CenterEdit edit : centerEdits)
		{
			edit.icon = null;
		}
		
		for (Map.Entry<Integer, CenterIcon> entry : centerIcons.entrySet())
		{
			int index = entry.getKey();
			CenterIcon icon = entry.getValue();
			centerEdits.get(index).icon = icon;
		}
	}
	
	private void storeCenterTrees(Map<Integer, CenterTrees> cTrees)
	{
		for (CenterEdit edit : centerEdits)
		{
			edit.trees = null;
		}
		
		for (Map.Entry<Integer, CenterTrees> entry : cTrees.entrySet())
		{
			int index = entry.getKey();
			CenterTrees trees = entry.getValue();
			centerEdits.get(index).trees = trees;
		}
	}
	
	public MapEdits deepCopy()
	{
		MapEdits copy = Helper.deepCopy(this);
		// Explicitly copy edits.text.areas because it isn't serializable. 
		if (text != null)
		{
			for (int i : new Range(text.size()))
			{
				MapText otherText = text.get(i);
				MapText resultText = copy.text.get(i);
				if (otherText.areas != null)
				{
					resultText.areas = new ArrayList<Area>(otherText.areas.size());
					for (Area area : otherText.areas)
					{
						resultText.areas.add(new Area(area));
					}
				}
			}
		}
		return copy;
	}
}
