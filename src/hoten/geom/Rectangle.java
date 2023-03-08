package hoten.geom;

/**
 * Rectangle.java
 *
 * @author Connor
 */
public class Rectangle
{

	final public double x, y, width, height;

	public Rectangle(double x, double y, double width, double height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public boolean liesOnAxes(Point p, double closeEnoughDistance)
	{
		return GenUtils.closeEnough(p.x, x, closeEnoughDistance) || GenUtils.closeEnough(p.y, y, closeEnoughDistance)
				|| GenUtils.closeEnough(p.x, getRight(), closeEnoughDistance)
				|| GenUtils.closeEnough(p.y, getBottom(), closeEnoughDistance);
	}

	public boolean inBounds(Point p)
	{
		return inBounds(p.x, p.y);
	}

	public boolean inBounds(double x0, double y0)
	{
		if (x0 < x || x0 > getRight() || y0 < y || y0 > getBottom())
		{
			return false;
		}
		return true;
	}
	
	public Rectangle add(Point point)
	{
		return add(point.x, point.y);
	}
	
	public Rectangle add(double xToAdd, double yToAdd)
	{
		double newX = x, newY = y, newWidth = width, newHeight = height;
		if (xToAdd > x + width)
		{
			newWidth = xToAdd - x;
		}
		else if (xToAdd < x)
		{
			newX = xToAdd;
		}
		
		if (yToAdd > y + height)
		{
			newHeight = yToAdd - y;
		}
		else if (yToAdd < y)
		{
			newY = yToAdd;
		}
		
		return new Rectangle(newX, newY, newWidth, newHeight);
	}
	
	/**
	 * Returns a new rectangle expanded to include both this rectangle and the one passed in.
	 */
	public Rectangle add(Rectangle other)
	{
		return add(other.x, other.y)
				.add(other.x, other.y + other.height)
				.add (other.x + other.width, other.y)
				.add(other.x + other.width, other.y + other.height);
	}
	
	/**
	 * Returns a new rectangle with the same centroid as this one but with the width and height expanded by the given width and height.
	 */
	public Rectangle pad(double paddWidth, double paddHeight)
	{
		return new Rectangle(x - paddWidth/2.0, y - paddHeight/2.0, width + paddWidth, height + paddHeight);
	}
	
	public double getRight()
	{
		return x + width;
	}
	
	public double getLeft()
	{
		return x;
	}

	public double getBottom()
	{
		return y + height;
	}

	public double getTop()
	{
		return y;
	}
}
