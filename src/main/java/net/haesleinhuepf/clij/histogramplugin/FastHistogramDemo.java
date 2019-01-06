package net.haesleinhuepf.clij.histogramplugin;

import clearcl.ClearCLBuffer;
import coremem.enums.NativeTypeEnum;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.kernels.Kernels;

import java.util.Arrays;
import java.util.HashMap;

/**
 * FastHistogramDemo
 * <p>
 * Author: @haesleinhuepf
 * January 2019
 */
public class FastHistogramDemo {
    public static void main(String... args) {

        String clDeviceName = "1070"; // enter the name of your GPU here. use CLIJ.getAvailableDeviceNames() to find out what's there

        CLIJ clij = CLIJ.getInstance(clDeviceName);

        ImagePlus input = NewImage.createShortImage("test", 1024, 1024, 128, NewImage.FILL_NOISE);

        long numberOfBins = 256;
        Float minimumGreyValue = 0f;
        Float maximumGreyValue = 65535f;

        ClearCLBuffer partialHistograms = clij.createCLBuffer(new long[]{numberOfBins, 1, input.getHeight()}, NativeTypeEnum.UnsignedShort);
        ClearCLBuffer dstHistogram = clij.createCLBuffer(new long[]{numberOfBins, 1, 1}, NativeTypeEnum.UnsignedShort);

        // we run it several times because the first iteration is usually slower than the following iterations
        for (int i = 0; i < 10; i ++) {
            
            long timeStamp = System.currentTimeMillis();
            ClearCLBuffer src = clij.convert(input, ClearCLBuffer.class);
            System.out.println("CPU->GPU conversion took " + (System.currentTimeMillis() - timeStamp) + " msec");

            
            timeStamp = System.currentTimeMillis();
            // determine partial histograms
            long[] globalSizes = new long[]{src.getHeight(),1, 1};
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("src", src);
            parameters.put("dst_histogram", partialHistograms);
            parameters.put("minimum", minimumGreyValue);
            parameters.put("maximum", maximumGreyValue);
            clij.execute(Histogram.class,
                    "histogram.cl",
                    "histogram_image_" + src.getDimension() + "d",
                    globalSizes,
                    parameters);

            src.close();

            // summarizing all partial histograms in one histogram
            Kernels.sumZProjection(clij, partialHistograms, dstHistogram);
            System.out.println("Histogram generation took " + (System.currentTimeMillis() - timeStamp) + " msec");

            // convert result back as float array to GPU
            timeStamp = System.currentTimeMillis();
            ImagePlus histogramImp = clij.convert(dstHistogram, ImagePlus.class);
            short[] determinedHistogram = (short[])(histogramImp.getProcessor().getPixels());
            System.out.println("GPU->CPU conversion took " + (System.currentTimeMillis() - timeStamp) + " msec");

            // output result
            System.out.println("Histogram: " + Arrays.toString(determinedHistogram));
            System.out.println("\n");

            // we do not close partialHistograms and dstHistogram as re-allocating them would take time
        }
        partialHistograms.close();
        dstHistogram.close();

        clij.close();

    }
}
