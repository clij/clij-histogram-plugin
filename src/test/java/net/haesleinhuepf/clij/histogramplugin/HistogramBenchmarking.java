package net.haesleinhuepf.clij.histogramplugin;

import clearcl.ClearCLBuffer;
import clearcl.util.ElapsedTime;
import ij.ImagePlus;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.kernels.Kernels;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.junit.Test;

import static net.haesleinhuepf.clij.histogramplugin.HistogramTest.getImageWithDefinedHistogram;
import static net.haesleinhuepf.clij.histogramplugin.HistogramTest.sumArray;

public class HistogramBenchmarking {

    Float minGreyValue = null;
    Float maxGreyValue = null;

    ClearCLBuffer input = null;
    RandomAccessibleInterval<FloatType> rai = null;

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

            long sumTimeGPU = 0;
            long sumTimeCPU = 0;

            sumTimeGPU += ElapsedTime.measureForceOutput("Conversion to GPU", () -> {
                input = clij.convert(imp50MB, ClearCLBuffer.class);
            });
            sumTimeCPU += ElapsedTime.measureForceOutput("Conversion to CPU", () -> {
                rai = ImageJFunctions.convertFloat(imp50MB);
            });

            ElapsedTime.measureForceOutput("Min/max determination on GPU", () -> {
                minGreyValue = new Double(Kernels.minimumOfAllPixels(clij, input)).floatValue();
                maxGreyValue = new Double(Kernels.maximumOfAllPixels(clij, input)).floatValue();
            });

            sumTimeGPU += ElapsedTime.measureForceOutput("Histogram on GPU", () -> {
                Histogram.histogram(clij, input, minGreyValue, maxGreyValue, numberOfBins);
            });
            sumTimeCPU += ElapsedTime.measureForceOutput("Histogram on CPU", () -> {
                determineHistogram(rai, minGreyValue, maxGreyValue, numberOfBins);
            });

            System.out.println("SUM CPU: " + sumTimeCPU);
            System.out.println("SUM GPU: " + sumTimeGPU);

            // checkImage(imp, referenceHistogram, 0f, 255f);


        }
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
}
