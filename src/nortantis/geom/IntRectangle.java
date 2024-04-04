package nortantis.geom;

public class IntRectangle
{

	final public int x, y, width, height;

	public IntRectangle(int x, int y, int width, int height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	public IntRectangle(IntPoint location, IntDimension size)
	{
		this(location.x, location.y, size.width, size.height);
	}

	public boolean contains(int x0, int y0)
	{
		if (x0 < x || x0 >= x + width || y0 < y || y0 >= y + height)
		{
			return false;
		}
		return true;
	}

	public boolean contains(IntPoint p)
	{
		return contains(p.x, p.y);
	}

	public boolean contains(IntRectangle other)
	{
		return contains(other.x, other.y) && contains(other.x + other.width, other.y + other.height);
	}
	
	/**
	 * Returns a new IntRectangle with the same centroid as this one (if the paddings are even numbers) but with the width and height expanded by the given width and height.
	 */
	public IntRectangle pad(int paddWidth, int paddHeight)
	{
		return new IntRectangle(x - paddWidth / 2, y - paddHeight / 2, width + paddWidth, height + paddHeight);
	}
	
	public IntRectangle add(IntPoint point)
	{
		return add(point.x, point.y);
	}

	public IntRectangle add(int xToAdd, int yToAdd)
	{
		int newX = x, newY = y, newWidth = width, newHeight = height;
		if (xToAdd > x + width)
		{
			newWidth = xToAdd - x;
		}
		else if (xToAdd < x)
		{
			newX = xToAdd;
			newWidth = width + (x - xToAdd);
		}

		if (yToAdd > y + height)
		{
			newHeight = yToAdd - y;
		}
		else if (yToAdd < y)
		{
			newY = yToAdd;
			newHeight = height + (y - yToAdd);
		}
		
		return new IntRectangle(newX, newY, newWidth, newHeight);
	}
	
	/**
	 * Returns a new rectangle expanded to include both this rectangle and the one passed in.
	 */
	public IntRectangle add(IntRectangle other)
	{
		return add(other.x, other.y).add(other.x, other.y + other.height).add(other.x + other.width, other.y).add(other.x + other.width,
				other.y + other.height);
	}
	
	public Rectangle toRectangle()
	{
		return new Rectangle(x, y, width, height);
	}
}
