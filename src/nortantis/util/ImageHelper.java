package nortantis.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.analysis.function.Sinc;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.imgscalr.Scalr.Method;
import org.jtransforms.fft.FloatFFT_2D;

import nortantis.ComplexArray;
import nortantis.MapSettings;
import nortantis.Stopwatch;
import nortantis.TextDrawer;
import nortantis.geom.Dimension;
import nortantis.geom.IntDimension;
import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Font;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import pl.edu.icm.jlargearrays.ConcurrencyUtils;

public class ImageHelper
{
	/**
	 * This should be called before closing the program if methods have been called which use jTransforms or other thread pools.
	 * 
	 * For some reason this doesn't need to be called when running a GUI, and will throw an errror if you do.
	 */
	public static void shutdownThreadPool()
	{
		ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination();
	}

	public static Dimension fitDimensionsWithinBoundingBox(Dimension maxDimensions, double originalWidth, double originalHeight)
	{
		double width = originalWidth;
		double height = originalHeight;
		if (originalWidth > maxDimensions.width)
		{
			width = maxDimensions.width;
			height = height * (width / originalWidth);
		}
		if (height > maxDimensions.height)
		{
			double prevHeight = height;
			height = maxDimensions.height;
			width = width * (height / prevHeight);
		}
		return new Dimension(width, height);
	}

	/**
	 * Converts a Image to type ImageType.Grayscale.
	 * 
	 * @param img
	 * @return
	 */
	public static Image convertToGrayscale(Image img)
	{
		return convertImageToType(img, ImageType.Grayscale8Bit);
	}

	/**
	 * Converts a Image to type ImageType.Grayscale.
	 * 
	 * @param img
	 * @return
	 */
	public static Image convertImageToType(Image img, ImageType type)
	{
		Image result = Image.create(img.getWidth(), img.getHeight(), type);
		Painter p = result.createPainter();
		p.drawImage(img, 0, 0);
		p.dispose();
		return result;
	}

	/**
	 * Scales the given image, preserving aspect ratio.
	 */
	public static Image scaleByWidth(Image inImage, int xSize)
	{
		return scaleByWidth(inImage, xSize, Method.QUALITY);
	}

	/**
	 * Scales the given image, preserving aspect ratio.
	 */
	public static Image scaleByWidth(Image inImage, int xSize, Method method)
	{
		int ySize = getHeightWhenScaledByWidth(inImage, xSize);
		return scale(inImage, xSize, ySize, method);
	}

	public static Image scale(Image inImage, int width, int height, Method method)
	{
		return inImage.scale(method, width, height);
	}

	public static int getHeightWhenScaledByWidth(Image inImage, int xSize)
	{
		double aspectRatio = ((double) inImage.getHeight()) / inImage.getWidth();
		int ySize = (int) (xSize * aspectRatio);
		if (ySize == 0)
			ySize = 1;
		return ySize;
	}

	public static int getWidthWhenScaledByHeight(Image inImage, int ySize)
	{
		double aspectRatioInverse = ((double) inImage.getWidth()) / inImage.getHeight();
		int xSize = (int) (aspectRatioInverse * ySize);
		if (xSize == 0)
			xSize = 1;
		return xSize;
	}

	/**
	 * Scales the given image, preserving aspect ratio.
	 */
	public static Image scaleByHeight(Image inImage, int ySize)
	{
		return scaleByHeight(inImage, ySize, Method.QUALITY);
	}

	/**
	 * Scales the given image, preserving aspect ratio.
	 */
	public static Image scaleByHeight(Image inImage, int ySize, Method method)
	{
		int xSize = getWidthWhenScaledByHeight(inImage, ySize);
		return inImage.scale(method, xSize, ySize);
	}


	/**
	 * Update one piece of a scaled image. Takes an area defined by boundsInSource and scales it into target. This implementation bicubic
	 * scaling is about five times slower than the one used by ImgScalr, but is much faster when I only want to update a small piece of a
	 * scaled image.
	 * 
	 * @param source
	 *            The unscaled image.
	 * @param target
	 *            The scaled image
	 * @param boundsInSource
	 *            The area in the source image that will be scaled and placed into the target image.
	 */
	public static void scaleInto(Image source, Image target, nortantis.geom.Rectangle boundsInSource)
	{
		boolean sourceHasAlpha = source.hasAlpha();
		boolean targetHasAlpha = target.hasAlpha();

		double scale = ((double) target.getWidth()) / ((double) source.getWidth());

		IntRectangle pixelsToUpdate;
		if (boundsInSource == null)
		{
			pixelsToUpdate = new IntRectangle(0, 0, target.getWidth(), target.getHeight());
		}
		else
		{
			int upperLeftX = Math.max(0, (int) (boundsInSource.x * scale));
			int upperLeftY = Math.max(0, (int) (boundsInSource.y * scale));
			// The +1's below are because I'm padding the width and height by 1
			// pixel to account for integer truncation.
			pixelsToUpdate = new IntRectangle(upperLeftX, upperLeftY,
					Math.min((int) (boundsInSource.width * scale) + 1, target.getWidth() - 1 - upperLeftX),
					Math.min((int) (boundsInSource.height * scale) + 1, target.getHeight() - 1 - upperLeftY));
		}

		for (int y = pixelsToUpdate.y; y < pixelsToUpdate.y + pixelsToUpdate.height; y++)
		{
			for (int x = pixelsToUpdate.x; x < pixelsToUpdate.x + pixelsToUpdate.width; x++)
			{
				int x1 = (int) (x / scale);
				int y1 = (int) (y / scale);
				int x2 = Math.min(x1 + 1, source.getWidth() - 1);
				int y2 = Math.min(y1 + 1, source.getHeight() - 1);
				double dx = x / scale - x1;
				double dy = y / scale - y1;
				Color c00 = Color.create(source.getRGB(x1, y1), sourceHasAlpha);
				Color c01 = Color.create(source.getRGB(x2, y1), sourceHasAlpha);
				Color c10 = Color.create(source.getRGB(x1, y2), sourceHasAlpha);
				Color c11 = Color.create(source.getRGB(x2, y2), sourceHasAlpha);
				int r0 = interpolate(c00.getRed(), c01.getRed(), c10.getRed(), c11.getRed(), dx, dy);
				int g0 = interpolate(c00.getGreen(), c01.getGreen(), c10.getGreen(), c11.getGreen(), dx, dy);
				int b0 = interpolate(c00.getBlue(), c01.getBlue(), c10.getBlue(), c11.getBlue(), dx, dy);
				if (targetHasAlpha)
				{
					int a0 = interpolate(c00.getAlpha(), c01.getAlpha(), c10.getAlpha(), c11.getAlpha(), dx, dy);
					target.setRGB(x, y, Color.create(r0, g0, b0, a0).getRGB());
				}
				else
				{
					target.setRGB(x, y, Color.create(r0, g0, b0).getRGB());
				}
			}
		}
	}

	public static int interpolate(int v00, int v01, int v10, int v11, double dx, double dy)
	{
		double v0 = v00 * (1 - dx) + v01 * dx;
		double v1 = v10 * (1 - dx) + v11 * dx;
		return (int) ((v0 * (1 - dy) + v1 * dy) + 0.5);
	}

	/**
	 * 
	 * @param size
	 *            Number of pixels from 3 standard deviations from one side of the Guassian to the other.
	 * @return
	 */
	public static float[][] createGaussianKernel(int size)
	{
		if (size == 0)
		{
			return new float[][] { { 1f } };
		}


		NormalDistribution dist = createDistributionForSize(size);
		int resultSize = (size * 2);

		float[][] kernel = new float[resultSize][resultSize];
		for (int x = 0; x < resultSize; x++)
		{
			double xDistanceFromCenter = Math.abs(resultSize / 2.0 - (x));
			for (int y = 0; y < resultSize; y++)
			{
				double yDistanceFromCenter = Math.abs(resultSize / 2.0 - (y));
				// Find the distance from the center (0,0).
				double distanceFromCenter = Math
						.sqrt(xDistanceFromCenter * xDistanceFromCenter + yDistanceFromCenter * yDistanceFromCenter);
				kernel[y][x] = (float) dist.density(distanceFromCenter);
			}
		}
		normalize(kernel);
		return kernel;
	}

	private static NormalDistribution createDistributionForSize(int size)
	{
		return new NormalDistribution(0, getStandardDeviationSizeForGaussianKernel(size));
	}

	public static float getGuassianMode(int kernelSize)
	{
		if (kernelSize == 0)
		{
			return 0f;
		}

		NormalDistribution dist = new NormalDistribution(0, getStandardDeviationSizeForGaussianKernel(kernelSize));
		return (float) dist.density(0.0);
	}

	private static double getStandardDeviationSizeForGaussianKernel(int kernelSize)
	{
		if (kernelSize == 0)
		{
			return 0f;
		}

		// I want the edge of the kernel to be 3 standard deviations away from
		// the middle. I also divide by 2 to get half of the size (the length
		// from center to edge).
		return kernelSize / (2.0 * 3.0);
	}

	public static float[][] createFractalKernel(int size, double p)
	{
		if (size == 0)
		{
			return new float[][] { { 1f } };
		}

		float[][] kernel = new float[size][size];
		for (int x : new Range(size))
		{
			double xDistanceFromCenter = Math.abs(size / 2.0 - x);
			for (int y : new Range(size))
			{
				double yDistanceFromCenter = Math.abs(size / 2.0 - y);
				// Find the distance from the center (0,0).
				double distanceFromCenter = Math
						.sqrt(xDistanceFromCenter * xDistanceFromCenter + yDistanceFromCenter * yDistanceFromCenter);
				kernel[y][x] = (float) (1.0 / (Math.pow(distanceFromCenter, p)));
			}
		}
		normalize(kernel);
		return kernel;
	}

	public static float[][] createPositiveSincKernel(int size, double scale)
	{
		if (size == 0)
		{
			return new float[][] { { 1f } };
		}

		Sinc dist = new Sinc();

		float[][] kernel = new float[size][size];
		for (int x : new Range(size))
		{
			double xDistanceFromCenter = Math.abs(size / 2.0 - x);
			for (int y : new Range(size))
			{
				double yDistanceFromCenter = Math.abs(size / 2.0 - y);
				// Find the distance from the center (0,0).
				double distanceFromCenter = Math
						.sqrt(xDistanceFromCenter * xDistanceFromCenter + yDistanceFromCenter * yDistanceFromCenter);
				kernel[y][x] = (float) dist.value(distanceFromCenter * scale);
				kernel[y][x] = Math.max(0, kernel[y][x]);
			}
		}
		normalize(kernel);
		return kernel;
	}

	/**
	 * Maximizes the contrast of the given grayscale image. The image must be a supported grayscale type.
	 */
	public static void maximizeContrastGrayscale(Image image)
	{
		if (!image.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Image type must a supported grayscale type, but was " + image.getType());
		}

		int maxPixelValue = image.getMaxPixelLevel();

		double min = maxPixelValue;
		double max = 0;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = image.getGrayLevel(x, y);
				if (value < min)
					min = value;
				if (value > max)
					max = value;
			}
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = image.getGrayLevel(x, y);
				int newValue = (int) (((value - min) / (max - min)) * maxPixelValue);
				image.setGrayLevel(x, y, newValue);
			}
		}
	}

	/*
	 * Scales values in the given array such that the minimum is targetMin, and the maximum is targetMax.
	 */
	public static void setContrast(float[][] array, float targetMin, float targetMax)
	{
		setContrast(array, targetMin, targetMax, 0, array.length, 0, array[0].length);
	}

	public static void setContrast(float[][] array, float targetMin, float targetMax, int rowStart, int rows, int colStart, int cols)
	{
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array[r][c];
				if (value < min)
					min = value;
				if (value > max)
					max = value;
			}
		}

		float range = max - min;
		float targetRange = targetMax - targetMin;
		
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array[r][c];
				array[r][c] = (((value - min) / (range))) * (targetRange) + targetMin;
			}
		}
	}

	public static void scaleLevels(float[][] array, float scale, int rowStart, int rows, int colStart, int cols)
	{
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				// Make sure the value is above 0. In theory this shouldn't
				// happen if the kernel is positive, but very small
				// values below zero can happen I believe due to rounding error.
				float value = Math.max(0f, array[r][c] * scale);
				if (value < 0f)
				{
					value = 0f;
				}
				else if (value > 1f)
				{
					value = 1f;
				}

				array[r][c] = value;
			}
		}
	}

	/**
	 * Multiplies each pixel by the given scale. The image must be a supported grayscale type
	 */
	public static void scaleGrayLevels(Image image, float scale)
	{
		if (!image.isGrayscaleOrBinary())
			throw new IllegalArgumentException("Image type must a supported grayscale type, but was " + image.getType());

		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = image.getGrayLevel(x, y);
				int newValue = (int) (value * scale);
				image.setGrayLevel(x, y, newValue);
			}
	}

	/**
	 * Normalizes the kernel such that the sum of its elements is 1.
	 */
	public static void normalize(float[][] kernel)
	{
		float sum = 0;
		for (float[] row : kernel)
		{
			for (float f : row)
			{
				sum += f;
			}
		}

		for (int r : new Range(kernel.length))
		{
			for (int c = 0; c < kernel[0].length; c++)
			{
				kernel[r][c] /= sum;
			}
		}
	}

	public static Image maskWithImage(Image image1, Image image2, Image mask)
	{
		return maskWithImage(image1, image2, mask, null);
	}

	/**
	 * Each pixel in the resulting image is a linear combination of that pixel from image1 and from image2 using the gray levels in the
	 * given mask. The mask must be ImageType.Grayscale.
	 * 
	 * @param region
	 *            Specifies only a region to create rather than masking the entire images.
	 */
	public static Image maskWithImage(Image image1, Image image2, Image mask, IntPoint image2OffsetInImage1)
	{
		if (image2OffsetInImage1 == null)
		{
			image2OffsetInImage1 = new IntPoint(0, 0);
		}
		final IntPoint image2OffsetInImage1Final = image2OffsetInImage1;

		if (mask.getType() != ImageType.Grayscale8Bit && mask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale" + " or TYPE_BYTE_BINARY.");

		if (image1.getWidth() != image2.getWidth())
			throw new IllegalArgumentException();
		if (image1.getHeight() != image2.getHeight())
			throw new IllegalArgumentException();
		if (image1.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is " + mask.getWidth() + " but image1 has width " + image1.getWidth() + ".");
		if (image1.getHeight() != mask.getHeight())
			throw new IllegalArgumentException();
		if (!new IntRectangle(0, 0, image2.getWidth(), image2.getHeight()).contains(image2OffsetInImage1))
		{
			throw new IllegalArgumentException("Region for masking is not contained within image2.");
		}

		Image result = Image.create(image1.getWidth(), image2.getHeight(), image1.getType());

		int numTasks = ThreadHelper.getInstance().getThreadCount();
		List<Runnable> tasks = new ArrayList<>(numTasks);
		int rowsPerJob = image1.getHeight() / numTasks;
		for (int taskNumber : new Range(numTasks))
		{
			tasks.add(() ->
			{
				int endY = taskNumber == numTasks - 1 ? image1.getHeight() : ((taskNumber + 1) * rowsPerJob);
				for (int y = (taskNumber * rowsPerJob); y < endY; y++)
					for (int x = 0; x < image1.getWidth(); x++)
					{
						Color color1 = Color.create(image1.getRGB(x, y));
						Color color2 = Color.create(image2.getRGB(x + image2OffsetInImage1Final.x, y + image2OffsetInImage1Final.y));
						float maskLevel = mask.getNormalizedPixelLevel(x, y);
						if (mask.getType() == ImageType.Grayscale8Bit)
						{
							maskLevel /= 255f;
						}

						int r = (int) (maskLevel * color1.getRed() + (1.0 - maskLevel) * color2.getRed());
						int g = (int) (maskLevel * color1.getGreen() + (1.0 - maskLevel) * color2.getGreen());
						int b = (int) (maskLevel * color1.getBlue() + (1.0 - maskLevel) * color2.getBlue());
						result.setRGB(x, y, r, g, b);
					}
			});
		}
		ThreadHelper.getInstance().processInParallel(tasks, true);

		return result;
	}

	/**
	 * Equivalent to combining a solid color image with an image and a mask in maskWithImage(...) except this way is more efficient.
	 */
	public static Image maskWithColor(Image image, Color color, Image mask, boolean invertMask)
	{
		if (image.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is " + mask.getWidth() + " but image has width " + image.getWidth() + ".");
		if (image.getHeight() != mask.getHeight())
			throw new IllegalArgumentException(
					"In maskWithColor, image height was " + image.getHeight() + " but mask height was " + mask.getHeight());

		return maskWithColorInRegion(image, color, mask, invertMask, new IntPoint(0, 0));
	}

	/**
	 * Equivalent to combining a solid color image with an image and a mask in maskWithImage(...) except this way is more efficient.
	 */
	public static Image maskWithColorInRegion(Image image, Color color, Image mask, boolean invertMask, IntPoint imageOffsetInMask)
	{
		if (mask.getType() != ImageType.Grayscale8Bit && mask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale.");

		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());

		// Process rows in parallel to speed things up a little. In my tests, doing so was 41% faster
		// (when only counting time in this method).
		int numTasks = ThreadHelper.getInstance().getThreadCount();
		List<Runnable> tasks = new ArrayList<>(numTasks);
		int rowsPerJob = image.getHeight() / numTasks;
		for (int taskNumber : new Range(numTasks))
		{
			tasks.add(() ->
			{
				int endY = taskNumber == numTasks - 1 ? image.getHeight() : (taskNumber + 1) * rowsPerJob;
				for (int y = taskNumber * rowsPerJob; y < endY; y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						int xInMask = x + imageOffsetInMask.x;
						int yInMask = y + imageOffsetInMask.y;
						if (xInMask < 0 || yInMask < 0 || xInMask >= mask.getWidth() || yInMask >= mask.getHeight())
						{
							continue;
						}

						Color col = Color.create(image.getRGB(x, y));

						int maskLevel = mask.getGrayLevel(xInMask, yInMask);
						if (mask.getType() == ImageType.Grayscale8Bit)
						{
							if (invertMask)
								maskLevel = 255 - maskLevel;

							int r = ((maskLevel * col.getRed()) + (255 - maskLevel) * color.getRed()) / 255;
							int g = ((maskLevel * col.getGreen()) + (255 - maskLevel) * color.getGreen()) / 255;
							int b = ((maskLevel * col.getBlue()) + (255 - maskLevel) * color.getBlue()) / 255;
							Color combined = Color.create(r, g, b, image.getAlphaLevel(x, y));
							result.setRGB(x, y, combined.getRGB());
						}
						else
						{
							// TYPE_BYTE_BINARY

							if (invertMask)
								maskLevel = 1 - maskLevel;

							int r = ((maskLevel * col.getRed()) + (1 - maskLevel) * color.getRed());
							int g = ((maskLevel * col.getGreen()) + (1 - maskLevel) * color.getGreen());
							int b = ((maskLevel * col.getBlue()) + (1 - maskLevel) * color.getBlue());
							Color combined = Color.create(r, g, b, image.getAlphaLevel(x, y));
							result.setRGB(x, y, combined.getRGB());
						}
					}
				}
			});
		}
		ThreadHelper.getInstance().processInParallel(tasks, true);

		return result;
	}

	/**
	 * Like maskWithColor except multiple colors can be specified.
	 * 
	 * @param colorIndexes
	 *            Each pixel stores a gray level which (converted to an int) is an index into colors.
	 */
	public static Image maskWithMultipleColors(Image image, Map<Integer, Color> colors, Image colorIndexes, Image mask, boolean invertMask)
	{
		if (mask.getType() != ImageType.Grayscale8Bit && mask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale or ImageType.Binary.");
		if (colorIndexes.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("colorIndexes type must be ImageType.Grayscale.");

		if (image.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is " + mask.getWidth() + " but image has width " + image.getWidth() + ".");
		if (image.getHeight() != mask.getHeight())
			throw new IllegalArgumentException();

		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());

		int numTasks = ThreadHelper.getInstance().getThreadCount();
		List<Runnable> tasks = new ArrayList<>(numTasks);
		int rowsPerJob = image.getHeight() / numTasks;
		for (int taskNumber : new Range(numTasks))
		{
			tasks.add(() ->
			{
				int endY = taskNumber == numTasks - 1 ? image.getHeight() : (taskNumber + 1) * rowsPerJob;
				for (int y = taskNumber * rowsPerJob; y < endY; y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						Color col = Color.create(image.getRGB(x, y));
						Color color = colors.get(colorIndexes.getGrayLevel(x, y));
						if (color != null)
						{
							int maskLevel = mask.getGrayLevel(x, y);
							if (mask.getType() == ImageType.Grayscale8Bit)
							{
								if (invertMask)
									maskLevel = 255 - maskLevel;

								int r = ((maskLevel * col.getRed()) + (255 - maskLevel) * color.getRed()) / 255;
								int g = ((maskLevel * col.getGreen()) + (255 - maskLevel) * color.getGreen()) / 255;
								int b = ((maskLevel * col.getBlue()) + (255 - maskLevel) * color.getBlue()) / 255;
								result.setRGB(x, y, r, g, b);
							}
							else
							{
								// TYPE_BYTE_BINARY

								if (invertMask)
									maskLevel = 255 - maskLevel;

								int r = ((maskLevel * col.getRed()) + (1 - maskLevel) * color.getRed());
								int g = ((maskLevel * col.getGreen()) + (1 - maskLevel) * color.getGreen());
								int b = ((maskLevel * col.getBlue()) + (1 - maskLevel) * color.getBlue());
								result.setRGB(x, y, r, g, b);
							}
						}
					}
				}
			});
		}
		ThreadHelper.getInstance().processInParallel(tasks, true);

		return result;

	}

	/**
	 * Creates a new Image in which the values of the given alphaMask to be the alpha channel in image.
	 * 
	 * @param image
	 * @param alphaMask
	 *            Must be type ImageType.Grayscale. It must also be the same dimension as image.
	 * @param invertMask
	 *            If true, the alpha values from alphaMask will be inverted.
	 * @return
	 */
	public static Image setAlphaFromMask(Image image, Image alphaMask, boolean invertMask)
	{
		if (image.getWidth() != alphaMask.getWidth())
			throw new IllegalArgumentException("Mask width is " + alphaMask.getWidth() + " but image has width " + image.getWidth() + ".");
		if (image.getHeight() != alphaMask.getHeight())
			throw new IllegalArgumentException();

		return setAlphaFromMaskInRegion(image, alphaMask, invertMask, new IntPoint(0, 0));
	}

	/**
	 * Creates a new Image in which the values of the given alphaMask to be the alpha channel in image.
	 * 
	 * @param image
	 * @param alphaMask
	 *            Must be type ImageType.Grayscale. It must also be the same dimension as image.
	 * @param invertMask
	 *            If true, the alpha values from alphaMask will be inverted.
	 * @param imageOffsetInMask
	 *            Used if the image is smaller than the mask, so only a piece of the mask should be used.
	 * @return A new image
	 */
	public static Image setAlphaFromMaskInRegion(Image image, Image alphaMask, boolean invertMask, IntPoint imageOffsetInMask)
	{
		if (alphaMask.getType() != ImageType.Grayscale8Bit && alphaMask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale or TYPE_BYTE_BINARY");

		Image result = Image.create(image.getWidth(), image.getHeight(), ImageType.ARGB);
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				int xInMask = x + imageOffsetInMask.x;
				int yInMask = y + imageOffsetInMask.y;
				if (xInMask < 0 || yInMask < 0 || xInMask >= alphaMask.getWidth() || yInMask >= alphaMask.getHeight())
				{
					continue;
				}

				int maskLevel = alphaMask.getGrayLevel(xInMask, yInMask);
				if (alphaMask.getType() == ImageType.Binary)
				{
					if (maskLevel == 1)
					{
						maskLevel = 255;
					}
				}
				if (invertMask)
				{
					maskLevel = 255 - maskLevel;
				}

				Color originalColor = image.getPixelColor(x, y);
				result.setPixelColor(x, y, Color.create(originalColor.getRed(), originalColor.getGreen(), originalColor.getBlue(), maskLevel));
			}
		return result;
	}

	public static Image createColoredImageFromGrayScaleImages(Image redChanel, Image greenChanel, Image blueChanel, Image alphaChanel)
	{
		if (redChanel.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("Red chanel image type must be type ImageType.Grayscale.");

		if (greenChanel.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("Green chanel image type must be type ImageType.Grayscale");

		if (blueChanel.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("Blue chanel image type must be type ImageType.Grayscale");

		if (alphaChanel.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("Alpha chanel image type must be type ImageType.Grayscale.");

		if (redChanel.getWidth() != alphaChanel.getWidth())
			throw new IllegalArgumentException(
					"Alpha chanel width is " + alphaChanel.getWidth() + " but red chanel image has width " + redChanel.getWidth() + ".");
		if (redChanel.getHeight() != alphaChanel.getHeight())
			throw new IllegalArgumentException();

		if (greenChanel.getWidth() != alphaChanel.getWidth())
			throw new IllegalArgumentException("Alpha chanel width is " + alphaChanel.getWidth() + " but green chanel image has width "
					+ greenChanel.getWidth() + ".");
		if (greenChanel.getHeight() != alphaChanel.getHeight())
			throw new IllegalArgumentException();

		if (blueChanel.getWidth() != alphaChanel.getWidth())
			throw new IllegalArgumentException(
					"Alpha chanel width is " + alphaChanel.getWidth() + " but blue chanel image has width " + blueChanel.getWidth() + ".");
		if (blueChanel.getHeight() != alphaChanel.getHeight())
			throw new IllegalArgumentException();

		Image result = Image.create(redChanel.getWidth(), redChanel.getHeight(), ImageType.ARGB);
		for (int y = 0; y < redChanel.getHeight(); y++)
			for (int x = 0; x < redChanel.getWidth(); x++)
			{
				int red = Color.create(redChanel.getRGB(x, y)).getRed();
				int green = Color.create(greenChanel.getRGB(x, y)).getGreen();
				int blue = Color.create(blueChanel.getRGB(x, y)).getBlue();

				int maskLevel = alphaChanel.getGrayLevel(x, y);

				result.setRGB(x, y, red, green, blue, maskLevel);
			}
		return result;
	}

	public static Image createBlackImage(int width, int height)
	{
		Image result = Image.create(width, height, ImageType.RGB);
		Painter p = result.createPainter();
		p.setColor(Color.black);
		p.drawRect(0, 0, result.getWidth(), result.getHeight());
		p.dispose();
		return result;
	}

	/**
	 * Extracts the specified region from image2, then makes the given mask be the alpha channel of that extracted region, then draws the
	 * extracted region onto image1.
	 * 
	 * @param image1
	 *            The image to draw to.
	 * @param image2
	 *            The background image which the mask indicates to pull pixel values from.
	 * @param mask
	 *            Pixel values tell how to combine values from image1 and image2.
	 * @param xLoc
	 *            X component of the upper left corner at which image2 pixel values will be drawn into image1, using the mask, before
	 *            rotation is applied.
	 * @param yLoc
	 *            Like xLoc, but for Y component.
	 * @param angle
	 *            Angle at which to rotate the mask before drawing into image 1. It will be rotated about the center of the mask.
	 */
	public static void combineImagesWithMaskInRegion(Image image1, Image image2, Image mask, int xLoc, int yLoc, double angle, Point pivot)
	{
		if (mask.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("Expected mask to be type ImageType.Grayscale.");

		if (image1.getWidth() != image2.getWidth())
			throw new IllegalArgumentException(
					"Image widths do not match. image1 width: " + image1.getWidth() + ", image 2 width: " + image2.getWidth());
		if (image1.getHeight() != image2.getHeight())
			throw new IllegalArgumentException();

		Image region = extractRotatedRegion(image2, xLoc, yLoc, mask.getWidth(), mask.getHeight(), angle, pivot);

		for (int y = 0; y < region.getHeight(); y++)
			for (int x = 0; x < region.getWidth(); x++)
			{
				int grayLevel = mask.getGrayLevel(x, y);
				Color r = Color.create(region.getRGB(x, y), true);

				// Don't clobber the alpha level from the region.
				int alphaLevel = Math.min(r.getAlpha(), grayLevel);

				// Only change the alpha channel of the region.
				region.setRGB(x, y, Color.create(r.getRed(), r.getGreen(), r.getBlue(), alphaLevel).getRGB());
			}

		Painter p = image1.createPainter();
		p.rotate(angle, pivot);
		p.drawImage(region, xLoc, yLoc);
	}

	public static int getAlphaLevel(Image image, int x, int y)
	{
		return Color.create(image.getRGB(x, y), true).getAlpha();
	}

	/**
	 * Creates an image the requested width and height that contains a region extracted from 'image', rotated at the given angle about the
	 * given pivot.
	 * 
	 * Warning: This adds an alpha channel, so the output image may not be the same type as the input image.
	 */
	public static Image extractRotatedRegion(Image image, int xLoc, int yLoc, int width, int height, double angle, Point pivot)
	{
		Image result = Image.create(width, height, ImageType.ARGB);
		Painter pResult = result.createPainter();
		pResult.rotate(-angle, pivot.x - xLoc, pivot.y - yLoc);
		pResult.translate(-xLoc, -yLoc);
		pResult.drawImage(image, 0, 0);

		return result;
	}

	/**
	 * 
	 * Creates a copy of a piece of an image.
	 * 
	 * It is important the the result is a copy even if the desired region is exactly the input.
	 */
	public static Image extractRegion(Image image, int xLoc, int yLoc, int width, int height)
	{
		Image result = Image.create(width, height, image.getType());
		Painter pResult = result.createPainter();
		pResult.translate(-xLoc, -yLoc);
		pResult.drawImage(image, 0, 0);

		return result;
	}

	/**
	 * Creates a rotated version of the input image 90 degrees either clockwise or counter-clockwise. =
	 */
	public static Image rotate90Degrees(Image image, boolean isClockwise)
	{
		Image result = Image.create(image.getHeight(), image.getWidth(), image.getType());
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (isClockwise)
				{
					result.setRGB(image.getHeight() - y - 1, image.getWidth() - x - 1, image.getRGB(x, y));
				}
				else
				{
					result.setRGB(y, x, image.getRGB(x, y));
				}
			}
		}

		return result;
	}

	public static void multiplyArrays(float[][] target, float[][] source)
	{
		assert target.length == source.length;
		assert target[0].length == source[0].length;

		for (int r = 0; r < target.length; r++)
		{
			for (int c = 0; c < target[r].length; c++)
			{
				target[r][c] *= source[r][c];
			}
		}
	}

	public static void drawIfPixelValueIsGreaterThanTarget(Image target, Image toDraw, int xLoc, int yLoc)
	{
		if (toDraw.getType() != ImageType.Binary)
		{
			throw new IllegalArgumentException("Unsupported buffered image type for toDraw. Actual type: " + toDraw.getType());
		}
		if (target.getType() != ImageType.Binary)
		{
			throw new IllegalArgumentException("Unsupported buffered image type for target. Actual type: " + target.getType());
		}

		for (int r = 0; r < toDraw.getHeight(); r++)
		{
			int targetR = yLoc + r;
			if (targetR < 0 || targetR >= target.getHeight())
			{
				continue;
			}
			for (int c = 0; c < toDraw.getWidth(); c++)
			{
				int targetC = xLoc + c;
				if (targetC < 0 || targetC >= target.getWidth())
				{
					continue;
				}

				int toDrawValue = toDraw.getGrayLevel(c, r);
				int targetValue = target.getGrayLevel(targetC, targetR);
				if (toDrawValue > targetValue)
				{
					target.setGrayLevel(targetC, targetR, toDrawValue);
				}
			}
		}
	}

	/**
	 * Convolves a gray-scale image and with a kernel. The input image is unchanged.
	 * 
	 * @param img
	 *            Image to convolve
	 * @param kernel
	 *            The kernel to convolve with.
	 * @param maximizeContrast
	 *            Iff true, the contrast of the convolved image will be maximized while it is still in floating point representation. In the
	 *            result the pixel values will range from 0 to 255 for 8 bit pixels, or 65535 for 16 bit. This is better than maximizing the
	 *            contrast of the result because the result is a Image, which has less precise values than floats.
	 * @param paddImageToAvoidWrapping
	 *            Normally, in wage convolution done using fast Fourier transforms will do wrapping when calculating values of pixels along
	 *            edges. Set this flag to add black padding pixels to the edge of the image to avoid this.
	 * @return The convolved image.
	 */
	public static Image convolveGrayscale(Image img, float[][] kernel, boolean maximizeContrast, boolean paddImageToAvoidWrapping)
	{
		ComplexArray data = convolveGrayscale(img, kernel, paddImageToAvoidWrapping);

		// Only use 16 bit pixels if the input image used them, to save memory.
		ImageType resultType = img.getType() == ImageType.Grayscale16Bit ? ImageType.Grayscale16Bit : ImageType.Grayscale8Bit;

		return realToImage(data, resultType, img.getWidth(), img.getHeight(), maximizeContrast, 0f, 1f, false, 0f);
	}

	/**
	 * Convolves a gray-scale image with a kernel. The input image is unchanged. The convolved image will be scaled while it is still in
	 * floating point representation. Values below 0 will be made 0. Values above 1 will be made 1.
	 * 
	 * @param img
	 *            Image to convolve
	 * @param kernel
	 * @param scale
	 *            Amount to multiply levels by.
	 * @param paddImageToAvoidWrapping
	 *            Normally, in wage convolution done using fast Fourier transforms will do wrapping when calculating values of pixels along
	 *            edges. Set this flag to add black padding pixels to the edge of the image to avoid this.
	 * @return The convolved image.
	 */
	public static Image convolveGrayscaleThenScale(Image img, float[][] kernel, float scale, boolean paddImageToAvoidWrapping)
	{
		ComplexArray data = convolveGrayscale(img, kernel, paddImageToAvoidWrapping);

		// Only use 16 bit pixels if the input image used them, to save memory.
		ImageType resultType = img.getType() == ImageType.Grayscale16Bit ? ImageType.Grayscale16Bit : ImageType.Grayscale8Bit;

		return realToImage(data, resultType, img.getWidth(), img.getHeight(), false, 0f, 0f, true, scale);
	}

	private static ComplexArray convolveGrayscale(Image img, float[][] kernel, boolean paddImageToAvoidWrapping)
	{
		int colsPaddingToAvoidWrapping = paddImageToAvoidWrapping ? kernel[0].length / 2 : 0;
		int cols = getPowerOf2EqualOrLargerThan(Math.max(img.getWidth() + colsPaddingToAvoidWrapping, kernel[0].length));
		int rowsPaddingToAvoidWrapping = paddImageToAvoidWrapping ? kernel.length / 2 : 0;
		int rows = getPowerOf2EqualOrLargerThan(Math.max(img.getHeight() + rowsPaddingToAvoidWrapping, kernel.length));
		// Make sure rows and cols are greater than 1 for JTransforms.
		if (cols < 2)
			cols = 2;
		if (rows < 2)
			rows = 2;

		ComplexArray data = forwardFFT(img, rows, cols);

		ComplexArray kernelData = forwardFFT(kernel, rows, cols, true);

		data.multiplyInPlace(kernelData);
		kernelData = null;

		// Do the inverse DFT on the product.
		inverseFFT(data);

		return data;
	}

	private static Image realToImage(ComplexArray data, ImageType type, int imageWidth, int imageHeight, boolean setContrast,
			float contrastMin, float contrastMax, boolean scaleLevels, float scale)
	{
		moveRealToLeftSide(data.getArrayJTransformsFormat());
		swapQuadrantsOfLeftSideInPlace(data.getArrayJTransformsFormat());

		int imgRowPaddingOver2 = (data.getHeight() - imageHeight) / 2;
		int imgColPaddingOver2 = (data.getWidth() - imageWidth) / 2;

		if (setContrast)
		{
			setContrast(data.getArrayJTransformsFormat(), contrastMin, contrastMax, imgRowPaddingOver2, imageHeight, imgColPaddingOver2,
					imageWidth);
		}
		else if (scaleLevels)
		{
			scaleLevels(data.getArrayJTransformsFormat(), scale, imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth);
		}

		Image result = arrayToImage(data.getArrayJTransformsFormat(), imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth,
				type);
		return result;
	}

	public static void inverseFFT(ComplexArray data)
	{
		FloatFFT_2D fft = new FloatFFT_2D(data.getHeight(), data.getWidth());
		fft.complexInverse(data.getArrayJTransformsFormat(), true);
	}

	public static ComplexArray forwardFFT(Image img, int rows, int cols)
	{
		ComplexArray data = new ComplexArray(cols, rows);

		int imgRowPadding = rows - img.getHeight();
		int imgColPadding = cols - img.getWidth();
		int imgRowPaddingOver2 = imgRowPadding / 2;
		int imgColPaddingOver2 = imgColPadding / 2;
		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);

		boolean isGrayscale = img.isGrayscaleOrBinary();
		float maxPixelValue = img.getMaxPixelLevel();

		for (int r = 0; r < img.getHeight(); r++)
		{
			for (int c = 0; c < img.getWidth(); c++)
			{
				float grayLevel = img.getGrayLevel(c, r);
				if (isGrayscale)
					grayLevel /= maxPixelValue;
				data.setRealInput(c + imgColPaddingOver2, r + imgRowPaddingOver2, grayLevel);
			}
		}

		// Do the forward FFT.
		fft.realForwardFull(data.getArrayJTransformsFormat());

		return data;
	}

	/**
	 * Do a 2D forward FFT.
	 * 
	 * @param input
	 * @param rows
	 *            Number of rows in the output
	 * @param cols
	 *            Number of columns in the output
	 * @param flipXAndYAxis
	 *            For kernels. Flip the kernel along the x and y axis as I get the values from it. This is needed to do convolution instead
	 *            of cross-correlation.
	 * @return
	 */
	public static ComplexArray forwardFFT(float[][] input, int rows, int cols, boolean flipXAndYAxis)
	{
		// Convert the kernel to the format required by JTransforms.
		ComplexArray data = new ComplexArray(cols, rows);

		int rowPadding = rows - input.length;
		int rowPaddingOver2 = rowPadding / 2;
		int colPadding = cols - input[0].length;
		int columnPaddingOver2 = colPadding / 2;
		for (int r = 0; r < input.length; r++) 
		{
			for (int c = 0; c < input[0].length; c++)
			{
				if (flipXAndYAxis)
				{
					data.setRealInput(c + columnPaddingOver2, r + rowPaddingOver2, input[input.length - 1 - r][input[0].length - 1 - c]);
				}
				else
				{
					data.setRealInput(c + columnPaddingOver2, r + rowPaddingOver2, input[r][c]);
				}
			}
		}

		// Do the forward FFT.
		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
		fft.realForwardFull(data.getArrayJTransformsFormat());

		return data;
	}

	public static void swapQuadrantsOfLeftSideInPlace(float[][] data)
	{
		int rows = data.length;
		int cols = data[0].length / 2;
		for (int r = 0; r < rows / 2; r++)
		{
			for (int c = 0; c < cols; c++)
			{
				if (c < cols / 2)
				{
					float temp = data[r + rows / 2][c + cols / 2];
					data[r + rows / 2][c + cols / 2] = data[r][c];
					data[r][c] = temp;
				}
				else
				{
					float temp = data[r + rows / 2][c - cols / 2];
					data[r + rows / 2][c - cols / 2] = data[r][c];
					data[r][c] = temp;
				}
			}
		}
	}

	public static void moveRealToLeftSide(float[][] data)
	{
		for (int r = 0; r < data.length; r++)
		{
			for (int c = 0; c < data[0].length / 2; c++)
			{
				data[r][c] = data[r][c * 2];
			}
		}
	}

	public static float[][] getRealPart(float[][] data)
	{
		float[][] result = new float[data.length][data[0].length / 2];
		for (int r = 0; r < data.length; r++)
			for (int c = 0; c < data[0].length / 2; c++)
			{
				result[r][c] = data[r][c * 2];
			}
		return result;
	}

	public static float[][] getImaginaryPart(float[][] data)
	{
		float[][] result = new float[data.length][data[0].length / 2];
		for (int r = 0; r < data.length; r++)
			for (int c = 0; c < data[0].length / 2; c++)
			{
				result[r][c] = data[r][c * 2 + 1];
			}
		return result;
	}

	public static Image arrayToImage(float[][] array, int rowStart, int rows, int colStart, int cols, ImageType imageType)
	{
		Image image = Image.create(cols, rows, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				int value = Math.min(maxPixelValue, (int) (array[r][c] * maxPixelValue));
				image.setGrayLevel(c - colStart, r - rowStart, value);
			}
		}
		return image;
	}

	public static Image arrayToImage(float[][] array, ImageType imageType)
	{
		Image image = Image.create(array[0].length, array.length, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				image.setGrayLevel(x, y, (int) (array[y][x] * maxPixelValue));
			}
		}
		return image;
	}

	public static float[][] imageToArray(Image img)
	{
		float[][] result = new float[img.getWidth()][img.getHeight()];
		for (int r = 0; r < img.getWidth(); r++)
		{
			for (int c = 0; c < img.getHeight(); c++)
			{
				result[r][c] = img.getGrayLevel(r, c);
			}
		}
		return result;
	}

	public static int getPowerOf2EqualOrLargerThan(int value)
	{
		return getPowerOf2EqualOrLargerThan((double) value);
	}

	public static int getPowerOf2EqualOrLargerThan(double value)
	{
		double logLength = Math.log(value) / Math.log(2.0);
		if (((int) logLength) == logLength)
		{
			return (int) value;
		}

		return (int) Math.pow(2.0, ((int) logLength) + 1.0);
	}

	public static float[][] genWhiteNoise(Random rand, int rows, int cols)
	{
		float[][] result = new float[rows][cols];
		for (int r : new Range(rows))
		{
			for (int c : new Range(cols))
			{
				result[r][c] = rand.nextFloat();
			}
		}
		return result;
	}

	public static float[][] tile(float[][] array, int targetRows, int targetCols, int rowOffset, int colOffset)
	{
		float[][] result = new float[targetRows][targetCols];
		for (int r = 0; r < result.length; r++)
			for (int c = 0; c < result[0].length; c++)
			{
				int arrayRow = (r + rowOffset) % array.length;
				;
				if (((r + rowOffset) / array.length) % 2 == 1)
					arrayRow = array.length - 1 - arrayRow;

				int arrayCol = (c + colOffset) % array[0].length;
				if (((c + colOffset) / array[0].length) % 2 == 1)
					arrayCol = array[0].length - 1 - arrayCol;

				result[r][c] = array[arrayRow][arrayCol];

			}

		return result;
	}

	public static Image tile(Image image, int targetRows, int targetCols)
	{
		return arrayToImage(tile(imageToArray(image), targetRows, targetCols, 0, 0), image.getType());
	}

	public static Image tileNTimes(Image image, int n)
	{
		return tile(image, image.getWidth() * n, image.getHeight() * n);
	}

	public static float[][] convertToTransformsInputFormat(float[][] transformReal, float[][] transformImaginary)
	{
		if (transformReal.length != transformImaginary.length)
			throw new IllegalArgumentException();
		if (transformReal[0].length != transformImaginary[0].length)
			throw new IllegalArgumentException();
		float[][] result = new float[transformReal.length][transformReal[0].length * 2];
		for (int r = 0; r < result.length; r++)
			for (int c = 0; c < result[0].length; c++)
			{
				result[r][c] = c % 2 == 0 ? transformReal[r][c / 2] : transformImaginary[r][c / 2];
			}
		return result;
	}

	/**
	 * Do histogram matching on an image.
	 * 
	 * @param target
	 *            The image to do histogram matching on.
	 * @param source
	 *            The source of histogram information.
	 * @param resultType
	 *            Image type of the result.
	 */
	public static Image matchHistogram(Image target, Image source, ImageType resultType)
	{
		HistogramEqualizer targetEqualizer = new HistogramEqualizer(target);
		HistogramEqualizer sourceEqualizer = new HistogramEqualizer(source);
		sourceEqualizer.imageType = resultType;

		sourceEqualizer.createInverse();

		// Equalize the target.
		Image targetEqualized = targetEqualizer.equalize(target);

		// Apply the inverse map to the equalized target.
		Image outImage = sourceEqualizer.inverseEqualize(targetEqualized);

		return outImage;

	}

	public static Image matchHistogram(Image target, Image source)
	{
		return matchHistogram(target, source, target.getType());
	}

	/**
	 * Creates a colored image from a grayscale one and a given color.
	 * 
	 * @param image
	 *            Grayscale image
	 * @param color
	 *            Color to use
	 * @param how
	 *            Algorithm to use when determining pixel colors
	 * @return
	 */
	public static Image colorify(Image image, Color color, ColorifyAlgorithm how)
	{
		return colorify(image, color, how, null);
	}

	/**
	 * Creates a colored image from a grayscale one and a given color.
	 * 
	 * @param image
	 *            Grayscale image
	 * @param color
	 *            Color to use
	 * @param how
	 *            Algorithm to use when determining pixel colors
	 * @param where
	 *            Allows colorifying only a snippet of image. Null means colorify the whole image.
	 * @return
	 */
	public static Image colorify(Image image, Color color, ColorifyAlgorithm how, IntRectangle where)
	{
		if (how == ColorifyAlgorithm.none)
		{
			return image;
		}

		if (where == null)
		{
			where = new IntRectangle(0, 0, image.getWidth(), image.getHeight());
		}

		if (image.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("The image must by type ImageType.Grayscale, but was type " + image.getType());
		Image result = Image.create(image.getWidth(), image.getHeight(), ImageType.RGB);

		float[] hsb = color.getHSB();
		final IntRectangle whereFinal = where;
		ThreadHelper.getInstance().processRowsInParallel(where.y, where.height, (y) ->
		{
			for (int x = whereFinal.x; x < whereFinal.x + whereFinal.width; x++)
			{
				float level = image.getNormalizedPixelLevel(x, y);
				result.setRGB(x, y, colorifyPixel(level, hsb, how));
			}
		});

		return result;
	}

	private static int colorifyPixel(float pixelLevelNormalized, float[] hsb, ColorifyAlgorithm how)
	{
		if (how == ColorifyAlgorithm.algorithm2)
		{
			float I = hsb[2] * 255f;
			float overlay = ((I / 255f) * (I + (2 * pixelLevelNormalized) * (255f - I))) / 255f;
			return Color.createFromHSB(hsb[0], hsb[1], overlay).getRGB();
		}
		else if (how == ColorifyAlgorithm.algorithm3)
		{
			float resultLevel;
			if (hsb[2] < 0.5f)
			{
				resultLevel = pixelLevelNormalized * (hsb[2] * 2f);
			}
			else
			{
				float range = (1f - hsb[2]) * 2;
				resultLevel = range * pixelLevelNormalized + (1f - range);
			}
			return Color.createFromHSB(hsb[0], hsb[1], resultLevel).getRGB();
		}
		else if (how == ColorifyAlgorithm.none)
		{
			return Color.createFromHSB(hsb[0], hsb[1], hsb[2]).getRGB();
		}
		else
		{
			throw new IllegalArgumentException("Unrecognize colorify algorithm.");
		}

	}

	public enum ColorifyAlgorithm
	{
		none, algorithm2, algorithm3 // algorithm3 preserves contrast a little
										// better than algorithm2.
	}

	/**
	 * Like colorify but for multiple colors. Colorifies an image using a an array of colors and a second image which maps those colors to
	 * pixels. This way you can specify multiple colors for the resulting image.
	 * 
	 * @param image
	 *            The image to colorify
	 * @param colorMap
	 *            Used as a map from region index (in politicalRegions) to region color.
	 * @param colorIndexes
	 *            Each pixel stores a gray level which (converted to an int) is an index into colors.
	 * @param how
	 *            Determines the algorithm to use for coloring pixels
	 * @param where
	 *            Allows colorifying only a snippet of image. If given, then it is assumed that colorIndex is possibly smaller than image,
	 *            and this point is the upper left corner in image where the result should be extracted from, using the width and height of
	 *            colorIndexes.
	 */
	public static Image colorifyMulti(Image image, Map<Integer, Color> colorMap, Image colorIndexes, ColorifyAlgorithm how, IntPoint where)
	{
		if (image.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("The image must by type ImageType.Grayscale, but was type " + image.getType());
		if (colorIndexes.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("colorIndexes type must be ImageType.Grayscale.");

		if (where == null)
		{
			where = new IntPoint(0, 0);
		}

		Image result = Image.create(colorIndexes.getWidth(), colorIndexes.getHeight(), ImageType.RGB);

		Map<Integer, float[]> hsbMap = new HashMap<>();

		for (int regionId : colorMap.keySet())
		{
			Color color = colorMap.get(regionId);
			float[] hsb = color.getHSB();
			hsbMap.put(regionId, hsb);
		}

		IntRectangle imageBounds = new IntRectangle(0, 0, image.getWidth(), image.getHeight());

		IntPoint whereFinal = where;
		ThreadHelper.getInstance().processRowsInParallel(0, colorIndexes.getHeight(), (y) ->
		{
			for (int x = 0; x < colorIndexes.getWidth(); x++)
			{
				if (!imageBounds.contains(x + whereFinal.x, y + whereFinal.y))
				{
					continue;
				}
				float level = image.getNormalizedPixelLevel(x + whereFinal.x, y + whereFinal.y);
				int colorKey = colorIndexes.getGrayLevel(x, y);
				float[] hsb = hsbMap.get(colorKey);
				// hsb can be null if a region edit is missing from the nort file. I saw this happen, but I don't know what caused it.
				// When it did happen, it happened to region 0, which is also the color index used for ocean, so I don't think there
				// is any functional impact to skipping drawing those pixels.
				if (hsb != null)
				{
					result.setRGB(x, y, colorifyPixel(level, hsb, how));
				}
			}
		});

		return result;
	}

	public static void write(Image image, String fileName)
	{
		image.write(fileName);
	}

	public static Image read(String fileName)
	{
		return Image.read(fileName);
	}

	/***
	 * Opens an image in the system default image editor.
	 * 
	 * @return The file name, in the system's temp folder.
	 */
	public static String openImageInSystemDefaultEditor(Image map, String filenameWithoutExtension) throws IOException
	{
		// Save the map to a file.
		String format = "png";
		File tempFile = File.createTempFile(filenameWithoutExtension, "." + format);
		map.write(tempFile.getAbsolutePath());

		openImageInSystemDefaultEditor(tempFile.getAbsolutePath());
		return tempFile.getAbsolutePath();
	}

	public static void openImageInSystemDefaultEditor(String imageFilePath)
	{
		// Attempt to open the map in the system's default image viewer.
		if (Desktop.isDesktopSupported())
		{
			Desktop desktop = Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.OPEN))
			{
				try
				{
					desktop.open(new File(imageFilePath));
				}
				catch (IOException e)
				{
					throw new RuntimeException(e);
				}
			}
		}
		else
		{
			throw new RuntimeException("Unable to open the map because Java's Desktop is not supported");
		}
	}

	public static int bound(int value)
	{
		return Math.min(255, Math.max(0, value));
	}

	public static float calcMeanOfGrayscaleImage(Image image)
	{
		long sum = 0;
		for (int r = 0; r < image.getHeight(); r++)
		{
			for (int c = 0; c < image.getWidth(); c++)
			{
				sum += image.getGrayLevel(c, r);
			}
		}

		return sum / ((float) (image.getHeight() * image.getWidth()));
	}

	public static float[] calcMeanOfEachColor(Image image)
	{
		float[] result = new float[3];
		for (int channel : new Range(3))
		{
			long sum = 0;
			for (int r = 0; r < image.getHeight(); r++)
			{
				for (int c = 0; c < image.getWidth(); c++)
				{
					Color color = Color.create(image.getRGB(c, r));
					int level;
					if (channel == 0)
					{
						level = color.getRed();
					}
					else if (channel == 1)
					{
						level = color.getGreen();
					}
					else
					{
						level = color.getBlue();
					}

					sum += level;
				}
			}
			result[channel] = sum / ((float) (image.getHeight() * image.getWidth()));
		}

		return result;
	}

	public static Image flipOnXAxis(Image image)
	{
		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				result.setRGB(image.getWidth() - x - 1, y, image.getRGB(x, y));
			}
		}

		return result;
	}

	public static Image flipOnYAxis(Image image)
	{
		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				result.setRGB(x, image.getHeight() - y - 1, image.getRGB(x, y));
			}
		}

		return result;
	}

	public static Image blur(Image image, int blurLevel, boolean paddImageToAvoidWrapping)
	{
		if (blurLevel == 0)
		{
			return image;
		}
		return ImageHelper.convolveGrayscale(image, ImageHelper.createGaussianKernel(blurLevel), false, paddImageToAvoidWrapping);
	}

	/**
	 * Changes all pixels in target to fillValue where pixels in source are between lowThreshold and highThreshold inclusive.
	 */
	public static void fillInTarget(Image target, Image source, int lowThreshold, int highThreshold, int fillValue)
	{
		if (!target.getSize().equals(source.getSize()))
		{
			throw new IllegalArgumentException(
					"Source and target must be the same size. Source size: " + source.getSize() + ", target size: " + target.getSize());
		}

		for (int y = 0; y < source.getHeight(); y++)
			for (int x = 0; x < source.getWidth(); x++)
			{
				int value = source.getGrayLevel(x, y);
				if (value >= lowThreshold && value <= highThreshold)
				{
					target.setGrayLevel(x, y, fillValue);
				}
			}
	}

	/**
	 * Thresholds values from toThreshold and then subtracts those values from from toSubtractFrom. threshold is not modified.
	 */
	public static void subtractThresholded(Image toThreshold, int threshold, int highValue, Image toSubtractFrom)
	{
		if (!toThreshold.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + toThreshold.getType());
		}

		if (!toSubtractFrom.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for subtracting from: " + toSubtractFrom.getType());
		}

		if (!toThreshold.getSize().equals(toSubtractFrom.getSize()))
		{
			throw new IllegalArgumentException("Images for thresholding and subtracting must be the same size. First size: "
					+ toThreshold.getSize() + ", second size: " + toSubtractFrom.getSize());
		}

		ThreadHelper.getInstance().processRowsInParallel(0, toThreshold.getHeight(), (y) ->
		{
			for (int x = 0; x < toThreshold.getWidth(); x++)
			{
				int thresholdedValue = toThreshold.getGrayLevel(x, y) >= threshold ? highValue : 0;
				toSubtractFrom.setGrayLevel(x, y, Math.max(0, toSubtractFrom.getGrayLevel(x, y) - thresholdedValue));
			}
		});
	}

	/**
	 * Thresholds values from toThreshold and then subtracts those values from from toSubtractFrom. threshold is not modified.
	 */
	public static void addThresholded(Image toThreshold, int threshold, int highValue, Image toAddTo)
	{
		if (!toThreshold.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + toThreshold.getType());
		}

		if (!toAddTo.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for adding to: " + toAddTo.getType());
		}

		if (!toThreshold.getSize().equals(toAddTo.getSize()))
		{
			throw new IllegalArgumentException("Images for thresholding and adding must be the same size. First size: "
					+ toThreshold.getSize() + ", second size: " + toAddTo.getSize());
		}

		ThreadHelper.getInstance().processRowsInParallel(0, toThreshold.getHeight(), (y) ->
		{
			for (int x = 0; x < toThreshold.getWidth(); x++)
			{
				int thresholdedValue = toThreshold.getGrayLevel(x, y) >= threshold ? highValue : 0;
				toAddTo.setGrayLevel(x, y, Math.max(0, toAddTo.getGrayLevel(x, y) + thresholdedValue));
			}
		});
	}

	public static void threshold(Image image, int threshold)
	{
		int maxPixelValue = image.getMaxPixelLevel();
		threshold(image, threshold, maxPixelValue);
	}

	/**
	 * Thresholds an image in-place
	 * 
	 * @param image
	 *            Input and output image.
	 * @param threshold
	 *            Pixel values equal to or greater than this value will be set to highValue. Pixel values lower than this will be set to 0.
	 * @param highValue
	 *            Value pixels will be set to if thresholded high.
	 */
	public static void threshold(Image image, int threshold, int highValue)
	{
		if (!image.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + image.getType());
		}

		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				int value = image.getGrayLevel(x, y);
				if (value >= threshold)
				{
					image.setGrayLevel(x, y, highValue);
				}
				else
				{
					image.setGrayLevel(x, y, 0);
				}
			}
	}

	public static void add(Image target, Image other)
	{
		if (!target.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for target: " + target.getType());
		}
		if (!other.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported other image type for target: " + other.getType());
		}

		int maxPixelValue = target.getMaxPixelLevel();
		for (int y = 0; y < target.getHeight(); y++)
			for (int x = 0; x < target.getWidth(); x++)
			{
				double value = (int) target.getGrayLevel(x, y);
				double otherValue = (int) other.getGrayLevel(x, y);
				target.setGrayLevel(x, y, (int) Math.min(maxPixelValue, value + otherValue));
			}
	}

	/**
	 * Subtracts other from target and stores the result in target.
	 * 
	 * @param target
	 *            Image to subtract from.
	 * @param other
	 *            Values to subtract.
	 */
	public static void subtract(Image target, Image other)
	{
		if (!target.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for subtracting: " + target.getType());
		}

		if (!other.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported other image type for subtracting: " + other.getType());
		}

		for (int y = 0; y < target.getHeight(); y++)
			for (int x = 0; x < target.getWidth(); x++)
			{
				int value = target.getGrayLevel(x, y);
				int otherValue = other.getGrayLevel(x, y);
				target.setGrayLevel(x, y, Math.max(0, value - otherValue));
			}
	}

	/**
	 * Extracts the snippet in source defined by boundsInSourceToCopyFrom and pastes that snippet into target at the location defined by
	 * upperLeftCornerToPasteIntoInTarget.
	 */
	public static void copySnippetFromSourceAndPasteIntoTarget(Image target, Image source, IntPoint upperLeftCornerToPasteIntoInTarget,
			IntRectangle boundsInSourceToCopyFrom, int widthOfBorderToNotDrawOn)
	{
		IntRectangle targetBounds = new IntRectangle(widthOfBorderToNotDrawOn, widthOfBorderToNotDrawOn,
				target.getWidth() - widthOfBorderToNotDrawOn * 2, target.getHeight() - widthOfBorderToNotDrawOn * 2);
		for (int y = 0; y < boundsInSourceToCopyFrom.height; y++)
		{
			for (int x = 0; x < boundsInSourceToCopyFrom.width; x++)
			{
				int targetX = x + upperLeftCornerToPasteIntoInTarget.x;
				int targetY = y + upperLeftCornerToPasteIntoInTarget.y;
				if (!targetBounds.contains(targetX, targetY))
				{
					continue;
				}

				int value = source.getRGB(x + boundsInSourceToCopyFrom.x, y + boundsInSourceToCopyFrom.y);
				target.setRGB(targetX, targetY, value);
			}
		}
	}

	public static Image copySnippet(Image source, IntRectangle boundsInSourceToCopyFrom)
	{
		IntRectangle sourceBounds = new IntRectangle(0, 0, source.getWidth(), source.getHeight());
		Image result = Image.create(boundsInSourceToCopyFrom.width, boundsInSourceToCopyFrom.height, source.getType());
		for (int y = 0; y < boundsInSourceToCopyFrom.height; y++)
		{
			for (int x = 0; x < boundsInSourceToCopyFrom.width; x++)
			{
				int sourceX = x + boundsInSourceToCopyFrom.x;
				int sourceY = y + boundsInSourceToCopyFrom.y;
				if (!sourceBounds.contains(sourceX, sourceY))
				{
					continue;
				}

				int value = source.getRGB(sourceX, sourceY);
				result.setRGB(x, y, value);
			}
		}

		return result;
	}

	public static Image createPlaceholderImage(String[] message)
	{
		if (message.length == 0)
		{
			return Image.create(1, 1, ImageType.ARGB);
		}

		Font font = MapSettings.parseFont("URW Chancery L\t0\t30");

		IntDimension textBounds = TextDrawer.getTextDimensions(message[0], font).toIntDimension();
		for (int i : new Range(1, message.length))
		{
			IntDimension lineBounds = TextDrawer.getTextDimensions(message[i], font).toIntDimension();
			textBounds = new IntDimension(Math.max(textBounds.width, lineBounds.width), textBounds.height + lineBounds.height);
		}

		Image placeHolder = Image.create((textBounds.width + 15), (textBounds.height + 20), ImageType.ARGB);
		Painter p = placeHolder.createPainter(DrawQuality.High);
		p.setFont(font);
		p.setColor(Color.create(168, 168, 168));

		int fontHeight = TextDrawer.getFontHeight(p);
		for (int i : new Range(message.length))
		{
			p.drawString(message[i], 14, fontHeight + (i * fontHeight));
		}

		return ImageHelper.scaleByWidth(placeHolder, placeHolder.getWidth(), Method.QUALITY);
	}
}
