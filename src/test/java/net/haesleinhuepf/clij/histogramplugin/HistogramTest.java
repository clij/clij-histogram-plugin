package net.haesleinhuepf.clij.histogramplugin;

import clearcl.ClearCLBuffer;
import clearcl.ClearCLImage;
import clearcl.util.ElapsedTime;
import coremem.enums.NativeTypeEnum;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.gui.WaitForUserDialog;
import ij.plugin.Duplicator;
import ij.process.ImageProcessor;
import ij.process.StackStatistics;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.kernels.Kernels;
import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class HistogramTest {
    @Test
    public void testHistogramFrom2DImage() {
        int imageWidth = 100;
        int imageHeight = 200;
        int imageDepth = 1;

        long[] referenceHistogram = new long[256];
        referenceHistogram[2] = 34;
        referenceHistogram[100] = 5;
        referenceHistogram[145] = 22;
        referenceHistogram[0] = imageWidth * imageHeight * imageDepth - sumArray(referenceHistogram);

        ImagePlus imp = getImageWithDefinedHistogram(imageWidth, imageHeight, imageDepth, referenceHistogram, 0, 255, 8);

        checkImage(imp, referenceHistogram, 0f, 255f);
    }

    @Test
    public void testHistogramFrom3DImage() {
        int imageWidth = 100;
        int imageHeight = 200;
        int imageDepth = 10;

        long[] referenceHistogram = new long[256];
        referenceHistogram[2] = 34;
        referenceHistogram[100] = 5;
        referenceHistogram[145] = 22;
        referenceHistogram[0] = imageWidth * imageHeight * imageDepth - sumArray(referenceHistogram);

        ImagePlus imp = getImageWithDefinedHistogram(imageWidth, imageHeight, imageDepth, referenceHistogram, 0, 255, 8);

        checkImage(imp, referenceHistogram, 0f, 255f);
    }

    Float minGreyValue = null;
    Float maxGreyValue = null;

    @Test
    public void testPerformanceOfGPUBasesHistogramGeneration() {
        for (int i = 0; i < 10; i++) {
            int imageWidth = 1024;
            int imageHeight = 1024;
            int imageDepth = 50;

            long[] referenceHistogram = new long[256];
            referenceHistogram[2] = 34;
            referenceHistogram[100] = 5;
            referenceHistogram[145] = 22;
            referenceHistogram[0] = imageWidth * imageHeight * imageDepth - sumArray(referenceHistogram);

            ImagePlus imp50MB = getImageWithDefinedHistogram(imageWidth, imageHeight, imageDepth, referenceHistogram, 0, 255, 8);

            int numberOfBins = 256;

            CLIJ clij = CLIJ.getInstance();

            ClearCLBuffer input = clij.convert(imp50MB, ClearCLBuffer.class);

            RandomAccessibleInterval<FloatType> rai = ImageJFunctions.convertFloat(imp50MB);

            ElapsedTime.measureForceOutput("Min/max determination on GPU", () -> {
                minGreyValue = new Double(Kernels.minimumOfAllPixels(clij, input)).floatValue();
                maxGreyValue = new Double(Kernels.maximumOfAllPixels(clij, input)).floatValue();

            });

            ElapsedTime.measureForceOutput("Histogram on GPU", () -> {
                Histogram.histogram(clij, input, minGreyValue, maxGreyValue, numberOfBins);
            });
            ElapsedTime.measureForceOutput("Histogram on CPU", () -> {
                determineHistogram(rai, minGreyValue, maxGreyValue, numberOfBins);
            });


            // checkImage(imp, referenceHistogram, 0f, 255f);


        }
    }

    private void checkImage(ImagePlus imp, long[] referenceHistogram, Float minGreyValue, Float maxGreyValue) {
        CLIJ clij = CLIJ.getInstance();

        ClearCLBuffer image = clij.convert(imp, ClearCLBuffer.class);

        int numberOfBins = 256;

        float[] determinedHistogram = Histogram.histogram(clij, image, minGreyValue, maxGreyValue, numberOfBins);

        System.out.print("reference histogram " + referenceHistogram.length + "\n");
        System.out.println(Arrays.toString(referenceHistogram));

        System.out.print("determined histogram " + determinedHistogram.length + "\n");
        System.out.println(Arrays.toString(determinedHistogram));

        long[] ijHistogram = imp.getStatistics().getHistogram();
        System.out.print("imagej histogram " + ijHistogram.length + "\n");
        System.out.println(Arrays.toString(ijHistogram));

        long[] cpuHistogram = imp.getStatistics().getHistogram();
        System.out.print("cpu histogram " + cpuHistogram.length + "\n");
        System.out.println(Arrays.toString(cpuHistogram));

        //new WaitForUserDialog("wait").show();

        assertTrue(compareArrays(referenceHistogram, determinedHistogram, 0));



    }

    private static ImagePlus getImageWithDefinedHistogram(int imageWidth, int imageHeight, int imageDepth, long[] histogram, float minimum, float maximum, int bitType) {
        ImagePlus imp = NewImage.createImage("test", imageWidth, imageHeight, imageDepth, bitType, NewImage.FILL_BLACK);

        int index = 1; // we don't start with 0 because all pixels are zero initially
        long count = histogram[index];

        for (int z = 0; z < imageDepth; z++) {
            imp.setSlice(z + 1);
            ImageProcessor ip = imp.getProcessor();
            for (int x = 0; x < imageWidth; x++) {
                for (int y = 0; y < imageHeight; y++) {
                    while(count == 0) {
                        index++;
                        if (index >= histogram.length) {
                            return imp;
                        }
                        count = histogram[index];
                    }

                    ip.setf(x, y, index);
                    count--;
                }
            }
        }
        return imp;
    }

    private long[] determineHistogram(RandomAccessibleInterval<FloatType> rai, float minimuGreyValue, float maximumGreyValue, int numberOfBins) {
        long[] histogram = new long[numberOfBins];

        float range = maximumGreyValue - minimuGreyValue;

        Cursor<FloatType> cursor = Views.iterable(rai).cursor();

        while (cursor.hasNext()) {
            int index = (int)(((cursor.next().get() - minimuGreyValue) / range) * (numberOfBins - 1));
            histogram[index]++;
        }

        return histogram;
    }

    private static long sumArray(long[] array) {
        long sum = 0;
        for (long item : array) {
            sum += item;
        }
        return sum;
    }

    public static boolean compareArrays(long[] a, float[] b, float tolerance) {
        if (a.length != b.length) {
            System.out.println("Array sizes differ");
            return false;
        }
        for (int x = 0; x < a.length; x++) {
            if(Math.abs(a[x] - b[x]) > tolerance) {
                System.out.println("Pixels[" + x + "] differ: " +
                        a[x] +
                        " != " +
                        b[x]
                );
                return false;
            }
        }
        return true;
    }
}