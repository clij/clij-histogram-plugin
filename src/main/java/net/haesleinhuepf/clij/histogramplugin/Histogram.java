package net.haesleinhuepf.clij.histogramplugin;

import clearcl.ClearCLBuffer;
import coremem.enums.NativeTypeEnum;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.process.ImageProcessor;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.kernels.Kernels;
import net.haesleinhuepf.clij.macro.AbstractCLIJPlugin;
import net.haesleinhuepf.clij.macro.CLIJMacroPlugin;
import net.haesleinhuepf.clij.macro.CLIJOpenCLProcessor;
import net.haesleinhuepf.clij.macro.documentation.OffersDocumentation;
import org.scijava.plugin.Plugin;

import java.util.HashMap;

/**
 *
 *
 * Author: @haesleinhuepf
 * 12 2018
 */
@Plugin(type = CLIJMacroPlugin.class, name = "CLIJ_convolve")
public class Histogram extends AbstractCLIJPlugin implements CLIJMacroPlugin, CLIJOpenCLProcessor, OffersDocumentation {

    @Override
    public boolean executeCL() {
        Integer numberOfBins = asInteger(args[2]);
        Float minimumGreyValue = asFloat(args[3]);
        Float maximumGreyValue = asFloat(args[4]);

        Object[] args = openCLBufferArgs();
        boolean result = fillHistogram(clij, (ClearCLBuffer)( args[0]), (ClearCLBuffer)(args[1]), minimumGreyValue, maximumGreyValue);
        releaseBuffers(args);

        ImagePlus histogramImp = clij.convert((ClearCLBuffer)(args[1]), ImagePlus.class);
        float[] determinedHistogram = (float[])(histogramImp.getProcessor().getPixels());

        float[] xAxis = new float[asInteger(args[2])];
        xAxis[0] = minimumGreyValue;
        float step = (maximumGreyValue - minimumGreyValue) / (numberOfBins - 1);

        for (int i = 1 ; i < xAxis.length; i ++) {
            xAxis[i] = xAxis[i-1] + step;
        }

        new Plot("Histogram", "grey value", "number of pixels", xAxis, determinedHistogram, 0).show();

        return result;
    }

    static boolean fillHistogram(CLIJ clij, ClearCLBuffer src, ClearCLBuffer dstHistogram, Float minimumGreyValue, Float maximumGreyValue) {

        long[] globalSizes = new long[]{src.getHeight(),src.getDepth(), 1};

        long numberOfPartialHistograms = globalSizes[0] * globalSizes[1] * globalSizes[2];
        long[] histogramBufferSize = new long[]{dstHistogram.getWidth(), 1, numberOfPartialHistograms};

        ClearCLBuffer partialHistograms = clij.createCLBuffer(histogramBufferSize, dstHistogram.getNativeType());

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("src", src);
        parameters.put("dst_histogram", partialHistograms);
        parameters.put("minimum", minimumGreyValue);
        parameters.put("maximum", maximumGreyValue);
        parameters.put("num_pixels_per_workitem", new Integer((int)src.getHeight()));

        //globalSizes[2] = 1; //src.getDepth();

        clij.execute(Histogram.class,
                "histogram.cl",
                "histogram_image_" + src.getDimension() + "d",
                globalSizes,
                parameters);

        Kernels.sumZProjection(clij, partialHistograms, dstHistogram);

        partialHistograms.close();

        return true;
    }

    public static float[] histogram(CLIJ clij, ClearCLBuffer image, Float minGreyValue, Float maxGreyValue, int numberOfBins) {
        ClearCLBuffer histogram = clij.createCLBuffer(new long[]{numberOfBins, 1, 1}, NativeTypeEnum.Float);

        if (minGreyValue == null) {
            minGreyValue = new Double(Kernels.minimumOfAllPixels(clij, image)).floatValue();
        }
        if (maxGreyValue == null) {
            maxGreyValue = new Double(Kernels.maximumOfAllPixels(clij, image)).floatValue();
        }

        Histogram.fillHistogram(clij, image, histogram, minGreyValue, maxGreyValue);

        ImagePlus histogramImp = clij.convert(histogram, ImagePlus.class);
        histogram.close();

        float[] determinedHistogram = (float[])(histogramImp.getProcessor().getPixels());
        return determinedHistogram;
    }

    @Override
    public String getParameterHelpText() {
        return "Image source,  Image destination, Number numberOfBins, Number minimumGreyValue, Number maximumGreyValue";
    }

    @Override
    public String getDescription() {
        return "Histogram the image with a given kernel image. Kernel image and source image should have the same\n" +
                "bit-type. Furthermore, it is recommended that the kernel image has an odd size in X, Y and Z.";
    }

    @Override
    public String getAvailableForDimensions() {
        return "2D, 3D";
    }

    @Override
    public ClearCLBuffer createOutputBufferFromSource(ClearCLBuffer input) {
        Integer numberOfBins = asInteger(args[2]);

        return clij.createCLBuffer(new long[]{numberOfBins,1,1},NativeTypeEnum.Float);
    }

}