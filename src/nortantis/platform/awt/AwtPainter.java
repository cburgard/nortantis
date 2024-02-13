package nortantis.platform.awt;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;

import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontMetrics;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.platform.Transform;

class AwtPainter extends Painter
{
	public Graphics2D g;
	
	public AwtPainter(Graphics2D graphics)
	{
		this.g = graphics;
	}

	@Override
	public void drawImage(Image image, int x, int y)
	{
		g.drawImage(((AwtImage)image).image, x, y, null);
	}

	@Override
	public void dispose()
	{
		g.dispose();
	}

	@Override
	public void drawRect(int x, int y, int width, int height)
	{
		g.drawRect(x, y, width, height);
	}

	@Override
	public void setColor(Color color)
	{
		g.setColor(((AwtColor)color).color);
	}

	@Override
	public void rotate(double angle, double pivotX, double pivotY)
	{
		g.rotate(angle, pivotX, pivotY);
	}

	@Override
	public void translate(double x, double y)
	{
		g.translate(x, y);
	}

	@Override
	public void setFont(Font font)
	{
		g.setFont(((AwtFont)font).font);
	}

	@Override
	public void drawString(String string, int x, int y)
	{
		g.drawString(string, x, y);
	}

	@Override
	public FontMetrics getFontMetrics()
	{
		return new AwtFontMetrics(g.getFontMetrics());
	}
	
	@Override
	public FontMetrics getFontMetrics(Font font)
	{
		return new AwtFontMetrics(g.getFontMetrics(((AwtFont)font).font));
	}

	@Override
	public void setTransform(Transform transform)
	{
		g.setTransform(((AwtTransform)transform).transform);
	}

	@Override
	public Transform getTransform()
	{
		return new AwtTransform(g.getTransform());
	}

	@Override
	public Font getFont()
	{
		return new AwtFont(g.getFont());
	}

	@Override
	public Color getColor()
	{
		return new AwtColor(g.getColor());
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints)
	{
		g.fillPolygon(xPoints, yPoints, xPoints.length);
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints)
	{
		g.drawPolygon(xPoints, yPoints, xPoints.length);
	}

	@Override
	public void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2)
	{
		g.setPaint(new java.awt.GradientPaint(x1, y1, ((AwtColor)color1).color, x2, y2, ((AwtColor)color2).color));
	}

	@Override
	public void setBasicStroke(float width)
	{
		g.setStroke(new BasicStroke(width));
	}

	@Override
	public void drawLine(int x1, int y1, int x2, int y2)
	{
		g.drawLine(x1, y1, x2, y2);
	}

	@Override
	public void fillOval(int x, int y, int width, int height)
	{
		g.fillOval(x, y, width, height);
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints)
	{
		g.drawPolyline(xPoints, yPoints, xPoints.length);
	}

	@Override
	public void fillRect(int x, int y, int width, int height)
	{
		g.fillRect(x, y, width, height);
	}

	@Override
	public void setDashedStroke(float width)
	{
		Stroke dashed = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, new float[] { 9 }, 0);
		g.setStroke(dashed);
	}

	@Override
	public void drawOval(int x, int y, int width, int height)
	{
		g.drawOval(x, y, width, height);
	}
}
