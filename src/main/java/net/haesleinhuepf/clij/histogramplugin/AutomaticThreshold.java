package net.haesleinhuepf.clij.histogramplugin;

import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.process.AutoThresholder;
import net.haesleinhuepf.clij.kernels.Kernels;
import net.haesleinhuepf.clij.macro.AbstractCLIJPlugin;
import net.haesleinhuepf.clij.macro.CLIJMacroPlugin;
import net.haesleinhuepf.clij.macro.CLIJOpenCLProcessor;
import net.haesleinhuepf.clij.macro.documentation.OffersDocumentation;
import org.scijava.plugin.Plugin;

import java.util.Arrays;

/**
 * AutomaticThreshold
 * <p>
 * Author: @haesleinhuepf
 * January 2019
 */
@Plugin(type = CLIJMacroPlugin.class, name = "CLIJ_automaticThreshold")
public class AutomaticThreshold extends AbstractCLIJPlugin implements CLIJMacroPlugin, CLIJOpenCLProcessor, OffersDocumentation {

    @Override
    public boolean executeCL() {
        Integer numberOfBins = 256;

        ClearCLBuffer src = (ClearCLBuffer) (args[0]);
        ClearCLBuffer dst = (ClearCLBuffer) (args[1]);
        String selectedThresholdMethod = (String) args[2];

        return applyAutomaticThreshold(clij, src, dst, numberOfBins, selectedThresholdMethod);

    }

    public static boolean applyAutomaticThreshold(CLIJ clij, ClearCLBuffer src, ClearCLBuffer dst, int numberOfBins, String selectedThresholdMethod)
    {
        // determine min and max intensity
        Float minimumGreyValue = 0f;
        Float maximumGreyValue = 0f;

        if (src.getNativeType() == NativeTypeEnum.UnsignedByte) {
            minimumGreyValue = 0f;
            maximumGreyValue = 255f;
        } else {
            minimumGreyValue = new Double(Kernels.minimumOfAllPixels(clij, src)).floatValue();
            maximumGreyValue = new Double(Kernels.maximumOfAllPixels(clij, src)).floatValue();
        }

        if (CLIJ.debug) {
            System.out.println("Minimum: " + minimumGreyValue);
            System.out.println("Maximum: " + maximumGreyValue);
        }

        // determine histogram
        ClearCLBuffer histogram = clij.createCLBuffer(new long[]{numberOfBins,1,1}, NativeTypeEnum.Float);
        Histogram.fillHistogram(clij, src, histogram, minimumGreyValue, maximumGreyValue);

        // the histogram is written in args[1] which is supposed to be a one-dimensional image
        ImagePlus histogramImp = clij.convert(histogram, ImagePlus.class);
        histogram.close();

        // convert histogram
        float[] determinedHistogram = (float[])(histogramImp.getProcessor().getPixels());
        int[] convertedHistogram = new int[determinedHistogram.length];

        long sum = 0;
        for (int i = 0; i < determinedHistogram.length; i++) {
            convertedHistogram[i] = (int)determinedHistogram[i];
            sum += convertedHistogram[i];
        }
        if (CLIJ.debug) {
            System.out.println("Sum: " + sum);
        }

        // check if given threshold method exists
        String method = "Default";

        for (String choice : AutoThresholder.getMethods()) {
            if (choice.toLowerCase().compareTo(selectedThresholdMethod.toLowerCase()) == 0) {
                method = choice;
            }
        }
        if (CLIJ.debug) {
            System.out.println("Threshold method: " + method);
        }

        float threshold = new AutoThresholder().getThreshold(method, convertedHistogram);

        // math source https://github.com/imagej/ImageJA/blob/master/src/main/java/ij/process/ImageProcessor.java#L692
        threshold = minimumGreyValue + ((threshold + 1.0f)/255.0f)*(maximumGreyValue-minimumGreyValue);

        if (CLIJ.debug) {
            System.out.println("Threshold: " + threshold);
        }

        // apply threshold
        clij.op().threshold(src, dst, threshold);

        return true;
    }

    @Override
    public String getDescription() {
        StringBuilder doc = new StringBuilder();
        doc.append("The automatic thresholder utilizes the threshold methods from ImageJ on a histogram determined on \n" +
                "the GPU to create binary images as similar as possible to ImageJ 'Apply Threshold' method. Enter one \n" +
                "of these methods in the method text field:\n" +
                Arrays.toString(AutoThresholder.getMethods()) );
        return doc.toString();
    }


    @Override
    public String getParameterHelpText() {
        return "Image input, Image destination, String method";
    }

    @Override
    public String getAvailableForDimensions() {
        return "2D, 3D";
    }
}
