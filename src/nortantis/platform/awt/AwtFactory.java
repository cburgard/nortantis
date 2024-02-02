package nortantis.platform.awt;

import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FilenameUtils;

import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PlatformFactory;
import nortantis.swing.SwingHelper;
import nortantis.util.ImageHelper;

public class AwtFactory extends PlatformFactory
{

	@Override
	public Image createImage(int width, int height, ImageType type)
	{
		return new AwtImage(width, height, type);
	}

	@Override
	public Image readImage(String filePath)
	{
		try
		{
			BufferedImage image = ImageIO.read(new File(filePath));
			if (image == null)
			{
				throw new RuntimeException(
						"Can't read the file " + filePath + ". This can happen if the file is an unsupported format or is corrupted, "
								+ "such as if you saved it with a file extension that doesn't match its actual format.");
			}

			return new AwtImage(image);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Can't read the file " + filePath);
		}
	}

	@Override
	public void writeImage(Image image, String filePath)
	{
		try
		{
			String extension = FilenameUtils.getExtension(filePath).toLowerCase();
			if (extension.equals("jpg") || extension.equals("jpeg"))
			{
				if (image.getType() == ImageType.ARGB)
				{
					// JPEG does not support transparency. Trying to write an
					// image with transparent pixels causes
					// it to silently not be created.
					image = ImageHelper.convertARGBtoRGB(image);
				}

				Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

				if (!writers.hasNext())
					throw new IllegalStateException("No writers found for jpg format.");

				ImageWriter writer = (ImageWriter) writers.next();
				OutputStream os = new FileOutputStream(new File(filePath));
				ImageOutputStream ios = ImageIO.createImageOutputStream(os);
				writer.setOutput(ios);

				ImageWriteParam param = writer.getDefaultWriteParam();

				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				final float quality = 0.95f;
				param.setCompressionQuality(quality);

				writer.write(null, new IIOImage(((AwtImage) ImageHelper.convertARGBtoRGB(image)).image, null, null), param);
			}
			else
			{
				ImageIO.write(((AwtImage) image).image, FilenameUtils.getExtension(filePath), new File(filePath));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public Font createFont(String name, FontStyle style, int size)
	{
		return new AwtFont(new java.awt.Font(name, style.value, size));
	}

	@Override
	public Color createColor(int rgb, boolean hasAlpha)
	{
		return new AwtColor(rgb, hasAlpha);
	}

	@Override
	public Color createColor(int red, int green, int blue)
	{
		return new AwtColor(red, green, blue);
	}


	@Override
	public Color createColor(float red, float green, float blue)
	{
		return new AwtColor(red, green, blue);
	}

	@Override
	public Color createColor(int red, int green, int blue, int alpha)
	{
		return new AwtColor(red, green, blue, alpha);
	}

	@Override
	public Color createColorFromHSB(float hue, float saturation, float brightness)
	{
		return Color.create(java.awt.Color.HSBtoRGB(hue, saturation, brightness));
	}

	public static BufferedImage unwrap(Image image)
	{
		return ((AwtImage) image).image;
	}

	public static Image wrap(BufferedImage image)
	{
		return new AwtImage(image);
	}

	public static Color wrap(java.awt.Color color)
	{
		return new AwtColor(color);
	}

	public static java.awt.Color unwrap(Color color)
	{
		return ((AwtColor) color).color;
	}

	public static Font wrap(java.awt.Font font)
	{
		return new AwtFont(font);
	}

	public static java.awt.Font unwrap(Font font)
	{
		return ((AwtFont) font).font;
	}

	public static java.awt.Rectangle toAwtRectangle(Rectangle rect)
	{
		return new java.awt.Rectangle((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height);
	}

	public static java.awt.Rectangle toAwtRectangle(IntRectangle rect)
	{
		return new java.awt.Rectangle(rect.x, rect.y, rect.width, rect.height);
	}

	public static java.awt.geom.Area toAwtArea(RotatedRectangle rect)
	{
		AffineTransform transform = new AffineTransform();
		transform.rotate(rect.angle, rect.pivotX, rect.pivotY);
		java.awt.Shape rotatedRect = transform
				.createTransformedShape(new java.awt.Rectangle((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height));
		return new java.awt.geom.Area(rotatedRect);
	}
	
	public static Painter wrap(java.awt.Graphics2D g)
	{
		return new AwtPainter(g);
	}

	@Override
	public boolean isFontInstalled(String fontFamily)
	{
		String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		for (String font : fonts)
		{
			if (font.equals(fontFamily))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public <T> void doInBackgroundThread(BackgroundTask<T> task)
	{
		SwingWorker<T, Void> worker = new SwingWorker<>()
		{
			@Override
			protected T doInBackground() throws Exception
			{
				return task.doInBackground();
			}

			@Override
			protected void done()
			{
				T result = null;
				try
				{
					result = get();
				}
				catch (InterruptedException ex)
				{
					throw new RuntimeException(ex);
				}
				catch (Exception ex)
				{
					SwingHelper.handleBackgroundThreadException(ex, null, false);
				}

				task.done(result);
			}
		};

		worker.execute();
	}

	@Override
	public void doInMainUIThread(Runnable toRun)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			toRun.run();
		}
		else
		{
			try
			{
				SwingUtilities.invokeAndWait(toRun);
			}
			catch (InvocationTargetException | InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}

	
}
