package nortantis.swing;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.imgscalr.Scalr.Method;

import nortantis.IconDrawer;
import nortantis.MapText;
import nortantis.WorldGraph;
import nortantis.editor.FreeIcon;
import nortantis.geom.Point;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;

@SuppressWarnings("serial")
public class MapEditingPanel extends UnscaledImagePanel
{
	private final Color highlightColor = new Color(255, 227, 74);
	private final Color waterHighlightColor = new Color(0, 193, 245);
	private final Color processingColor = Color.orange;
	private final Color selectColor = Color.orange;
	private Set<Center> highlightedCenters;
	private Set<Center> selectedCenters;
	private WorldGraph graph;
	private HighlightMode highlightMode;
	private Collection<Edge> highlightedEdges;
	private boolean highlightLakes;
	private boolean highlightRivers;
	public BufferedImage mapFromMapCreator;
	private java.awt.Point brushLocation;
	private int brushDiameter;
	private double zoom;
	private double resolution;
	private int borderWidth;
	private Point textBoxLocation;
	private Rectangle textBoxBoundsLine1;
	private double textBoxAngle;
	private BufferedImage rotateTextIconScaled;
	private Area rotateToolArea;
	private BufferedImage moveTextIconScaled;
	private Area moveToolArea;
	private Rectangle textBoxBoundsLine2;
	private Set<Area> highlightedAreas;
	private Set<RotatedRectangle> processingAreas;

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		selectedCenters = new HashSet<>();
		highlightedEdges = new HashSet<>();
		highlightedAreas = new HashSet<>();
		processingAreas = new HashSet<>();
		zoom = 1.0;
		resolution = 1.0;
	}

	public void showBrush(java.awt.Point location, int brushDiameter)
	{
		brushLocation = location;
		this.brushDiameter = brushDiameter;
	}

	public void hideBrush()
	{
		brushLocation = null;
		brushDiameter = 0;
	}

	public void addHighlightedEdges(Collection<Edge> edges)
	{
		highlightedEdges.addAll(edges);
	}

	public void addHighlightedEdge(Edge edge)
	{
		highlightedEdges.add(edge);
	}

	public void clearHighlightedEdges()
	{
		highlightedEdges.clear();
	}

	public void setTextBoxToDraw(nortantis.geom.Point location, nortantis.geom.Rectangle line1Bounds, nortantis.geom.Rectangle line2Bounds, double angle)
	{
		this.textBoxLocation = location == null ? null : new nortantis.geom.Point(location);
		this.textBoxBoundsLine1 = line1Bounds == null ? null : AwtFactory.toAwtRectangle(line1Bounds);
		this.textBoxBoundsLine2 = line2Bounds == null ? null : AwtFactory.toAwtRectangle(line2Bounds);
		this.textBoxAngle = angle;
	}

	public void setTextBoxToDraw(MapText text)
	{
		this.textBoxLocation = text.location == null ? null : new nortantis.geom.Point(text.location);
		this.textBoxBoundsLine1 = text.line1Bounds == null ? null : AwtFactory.toAwtRectangle(text.line1Bounds);
		this.textBoxBoundsLine2 = text.line2Bounds == null ? null : AwtFactory.toAwtRectangle(text.line2Bounds);
		this.textBoxAngle = text.angle;
	}

	public void clearTextBox()
	{
		this.textBoxLocation = null;
		this.textBoxBoundsLine1 = null;
		this.textBoxBoundsLine2 = null;
		this.textBoxAngle = 0.0;
	}

	public void setHighlightedAreasFromTexts(List<MapText> texts)
	{
		highlightedAreas.clear();
		for (MapText text : texts)
		{
			if (text.line1Area != null)
			{
				highlightedAreas.add(AwtFactory.toAwtArea(text.line1Area));
			}

			if (text.line2Area != null)
			{
				highlightedAreas.add(AwtFactory.toAwtArea(text.line2Area));
			}
		}
	}
	
	public void setHighlightedAreasFromIcons(IconDrawer iconDrawer, List<FreeIcon> icons)
	{
		highlightedAreas.clear();
		for (FreeIcon icon :icons)
		{
			nortantis.geom.Rectangle bounds = iconDrawer.toIconDrawTask(icon).createBounds();
			highlightedAreas.add(AwtFactory.toAwtArea(bounds));
		}
	}

	public void clearHighlightedAreas()
	{
		highlightedAreas.clear();
	}

	public void addProcessingAreasFromTexts(List<MapText> texts)
	{
		for (MapText text : texts)
		{
			if (text.line1Area != null)
			{
				processingAreas.add(text.line1Area);
			}

			if (text.line2Area != null)
			{
				processingAreas.add(text.line2Area);
			}
		}
	}
	
	public void addProcessingAreas(Set<RotatedRectangle> areas)
	{
		processingAreas.addAll(areas);
	}

	public void removeProcessingAreas(Set<RotatedRectangle> areasToRemove)
	{
		for (RotatedRectangle area : areasToRemove)
		{
			processingAreas.remove(area);
		}
	}

	public void clearProcessingAreas()
	{
		processingAreas.clear();
	}

	public void addHighlightedCenter(Center c)
	{
		highlightedCenters.add(c);
	}

	public void addHighlightedCenters(Collection<Center> centers)
	{
		highlightedCenters.addAll(centers);
	}

	public void clearHighlightedCenters()
	{
		if (highlightedCenters != null)
			highlightedCenters.clear();
	}

	public void addSelectedCenter(Center c)
	{
		selectedCenters.add(c);
	}

	public void addSelectedCenters(Collection<Center> centers)
	{
		selectedCenters.addAll(centers);
	}

	public void clearSelectedCenters()
	{
		if (selectedCenters != null)
		{
			selectedCenters.clear();
		}
	}

	public void setHighlightLakes(boolean enabled)
	{
		this.highlightLakes = enabled;
	}

	public void setHighlightRivers(boolean enabled)
	{
		this.highlightRivers = enabled;
	}

	public void setGraph(WorldGraph graph)
	{
		this.graph = graph;
	}

	public void setCenterHighlightMode(HighlightMode mode)
	{
		this.highlightMode = mode;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2 = ((Graphics2D) g);
		if (brushLocation != null)
		{
			g.setColor(highlightColor);
			drawBrush(g2);
		}


		// Handle zoom and border width. This transform transforms from graph
		// space to image space.
		AffineTransform transform = new AffineTransform();
		transform.scale(zoom, zoom);
		transform.translate(borderWidth, borderWidth);
		((Graphics2D) g).transform(transform);

		// Handle drawing/highlighting

		if (textBoxBoundsLine1 != null)
		{
			drawTextBox(((Graphics2D) g));
		}

		drawAreas(g);

		if (graph != null)
		{
			if (highlightLakes)
			{
				drawLakes(g);
			}

			if (highlightRivers)
			{
				drawRivers(g);
			}

			g.setColor(highlightColor);
			drawCenterOutlines(g, highlightedCenters);
			drawEdges(g, highlightedEdges);

			g.setColor(selectColor);
			drawCenterOutlines(g, selectedCenters);
		}
	}

	private void drawTextBox(Graphics2D g)
	{
		Graphics2D g2 = ((Graphics2D) g);
		g.setColor(highlightColor);
		AffineTransform originalTransformCopy = g2.getTransform();

		double centerX = textBoxLocation.x * resolution;
		double centerY = textBoxLocation.y * resolution;

		// Rotate the area
		g2.rotate(textBoxAngle, centerX, centerY);

		int padding = (int) (9 * resolution);
		g2.drawRect((int) (textBoxBoundsLine1.x + centerX), (int) (textBoxBoundsLine1.y + centerY), textBoxBoundsLine1.width,
				textBoxBoundsLine1.height);
		if (textBoxBoundsLine2 != null)
		{
			g2.drawRect((int) (textBoxBoundsLine2.x + centerX), (int) (textBoxBoundsLine2.y + centerY), textBoxBoundsLine2.width,
					textBoxBoundsLine2.height);
		}

		// Place the image for the rotation tool.
		{
			int x;
			if (textBoxBoundsLine2 == null)
			{
				x = (int) (textBoxBoundsLine1.x + centerX) + textBoxBoundsLine1.width + padding;
			}
			else
			{
				x = Math.max((int) (textBoxBoundsLine1.x + centerX) + textBoxBoundsLine1.width,
						(int) (textBoxBoundsLine2.x + centerX) + textBoxBoundsLine2.width) + padding;
			}
			int y;
			if (textBoxBoundsLine2 == null)
			{
				y = (int) (textBoxBoundsLine1.y + centerY) + (textBoxBoundsLine1.height / 2) - (rotateTextIconScaled.getHeight() / 2);
			}
			else
			{
				y = (int) (centerY - (rotateTextIconScaled.getHeight() / 2.0));
			}
			g2.drawImage(rotateTextIconScaled, x, y, null);
			rotateToolArea = new Area(new Ellipse2D.Double(x, y, rotateTextIconScaled.getWidth(), rotateTextIconScaled.getHeight()));
			rotateToolArea.transform(g2.getTransform());
		}

		// Place the image for the move tool.
		{
			int x = (int) (textBoxBoundsLine1.x + centerX) + (int) (Math.round(textBoxBoundsLine1.width / 2.0))
					- (int) (Math.round(moveTextIconScaled.getWidth() / 2.0));
			int y = (int) (textBoxBoundsLine1.y + centerY) - (moveTextIconScaled.getHeight()) - padding;
			g2.drawImage(moveTextIconScaled, x, y, null);
			moveToolArea = new Area(new Ellipse2D.Double(x, y, moveTextIconScaled.getWidth(), moveTextIconScaled.getHeight()));
			moveToolArea.transform(g2.getTransform());
		}

		g2.setTransform(originalTransformCopy);
	}

	public boolean isInTextRotateTool(java.awt.Point point)
	{
		if (rotateToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return rotateToolArea.contains(tPoint);
	}

	public boolean isInTextMoveTool(java.awt.Point point)
	{
		if (moveToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return moveToolArea.contains(tPoint);
	}

	private void drawBrush(Graphics2D g)
	{
		AffineTransform t = g.getTransform();
		g.setTransform(transformWithOsScaling);
		g.drawOval(brushLocation.x - brushDiameter / 2, brushLocation.y - brushDiameter / 2, brushDiameter, brushDiameter);
		g.setTransform(t);
	}

	private void drawAreas(Graphics g)
	{
		if (!highlightedAreas.isEmpty())
		{
			g.setColor(highlightColor);
			for (Area area : highlightedAreas)
			{
				((Graphics2D) g).draw(area);
			}
		}

		if (!processingAreas.isEmpty())
		{
			g.setColor(processingColor);
			for (RotatedRectangle rect : processingAreas)
			{
				Area area = AwtFactory.toAwtArea(rect);
				((Graphics2D) g).draw(area);
			}
		}

	}

	private void drawCenterOutlines(Graphics g, Set<Center> centers)
	{
		for (Center c : centers)
		{
			for (Edge e : c.borders)
			{
				if (highlightMode == HighlightMode.outlineGroup)
				{
					if (e.d0 != null && e.d1 != null && centers.contains(e.d0) && centers.contains(e.d1))
					{
						// c is not on the edge of the group
						continue;
					}
				}
				graph.drawEdge(AwtFactory.wrap((Graphics2D) g), e);
			}
		}
	}

	private void drawEdges(Graphics g, Collection<Edge> edges)
	{
		for (Edge e : edges)
		{
			graph.drawEdge(AwtFactory.wrap((Graphics2D) g), e);
		}

	}

	private void drawLakes(Graphics g)
	{
		g.setColor(waterHighlightColor);
		for (Center c : graph.centers)
		{
			if (c.isLake)
			{
				for (Edge e : c.borders)
				{
					if (e.d0 != null && e.d1 != null && e.d0.isLake && e.d1.isLake)
					{
						// c is not on the edge of the group
						continue;
					}
					graph.drawEdge(AwtFactory.wrap((Graphics2D) g), e);
				}
			}
		}
	}

	private void drawRivers(Graphics g)
	{
		g.setColor(waterHighlightColor);
		graph.drawRivers(AwtFactory.wrap((Graphics2D) g), null, null);
	}

	public void setZoom(double zoom)
	{
		this.zoom = zoom;
	}

	public void setResolution(double resolution)
	{
		this.resolution = resolution;

		// Determines the size at which the rotation and move tool icons appear.
		final double iconScale = 0.2;

		BufferedImage rotateIcon = AwtFactory.unwrap(ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal", "rotate text.png").toString()));
		rotateTextIconScaled = AwtFactory.unwrap(ImageHelper.scaleByWidth(AwtFactory.wrap(rotateIcon), (int) (rotateIcon.getWidth() * resolution * iconScale),
				Method.ULTRA_QUALITY));
		BufferedImage moveIcon = AwtFactory.unwrap(ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal", "move text.png").toString()));
		moveTextIconScaled = AwtFactory.unwrap(ImageHelper.scaleByWidth(AwtFactory.wrap(moveIcon), (int) (moveIcon.getWidth() * resolution * iconScale), Method.ULTRA_QUALITY));
	}

	public void setBorderWidth(int borderWidth)
	{
		this.borderWidth = borderWidth;
	}

	public void clearAllSelectionsAndHighlights()
	{
		clearTextBox();
		clearSelectedCenters();
		clearHighlightedCenters();
		clearHighlightedEdges();
		hideBrush();
		clearHighlightedAreas();
		clearProcessingAreas();
		highlightRivers = false;
		highlightLakes = false;
		repaint();
	}
}
