package net.haesleinhuepf.clij.histogramplugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.WaitForUserDialog;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * AutomaticThresholdTest
 * <p>
 * Author: @haesleinhuepf
 * January 2019
 */
public class AutomaticThresholdTest {
    @Test
    public void testThresholdDefault() {
        new ImageJ();
        CLIJ clij = CLIJ.getInstance("1070");

        for (String thresholdMethod : AutoThresholder.getMethods()) {

            // threshold with ImageJ
            ImagePlus blobs1 = IJ.openImage("src/test/resources/blobs.gif");
            blobs1.show();
            Prefs.blackBackground = false;
            IJ.setAutoThreshold(blobs1, thresholdMethod);
            IJ.run(blobs1, "Convert to Mask", "");
            IJ.run(blobs1, "Invert LUT", "");

            // threshold with clij
            ImagePlus blobs2 = IJ.openImage("src/test/resources/blobs.gif");

            ClearCLBuffer input = clij.push(blobs2);
            ClearCLBuffer thresholded = clij.create(input);
            ClearCLBuffer result = clij.create(input);
            AutomaticThreshold.applyAutomaticThreshold(clij, input, thresholded, 256, thresholdMethod);

            // compare results
            clij.op().multiplyImageAndScalar(thresholded, result, 255f);

            ImagePlus clijResultBlobs = clij.pull(result);
            result.close();
            input.close();

            clijResultBlobs.show();

            //new WaitForUserDialog("wait").show();

            System.out.println("Method: " + thresholdMethod);
            assertTrue(compareImages(blobs1, clijResultBlobs, 0));
            //break;
            IJ.run("Close All");
        }

        clij.close();
        IJ.exit();

    }

    public static boolean compareImages(ImagePlus a, ImagePlus b, double tolerance)
    {
        if (a.getWidth() != b.getWidth()
                || a.getHeight() != b.getHeight()
                || a.getNChannels() != b.getNChannels()
                || a.getNFrames() != b.getNFrames()
                || a.getNSlices() != b.getNSlices())
        {
            System.out.println("sizes different");
            System.out.println("w " + a.getWidth() + " != " + b.getWidth());
            System.out.println("h " + a.getHeight() + " != " + b.getHeight());
            System.out.println("c " + a.getNChannels() + " != " + b.getNChannels());
            System.out.println("f " + a.getNFrames() + " != " + b.getNFrames());
            System.out.println("s " + a.getNSlices() + " != " + b.getNSlices());
            return false;
        }

        for (int c = 0; c < a.getNChannels(); c++)
        {
            a.setC(c + 1);
            b.setC(c + 1);
            for (int t = 0; t < a.getNFrames(); t++)
            {
                a.setT(t + 1);
                b.setT(t + 1);
                for (int z = 0; z < a.getNSlices(); z++)
                {
                    a.setZ(z + 1);
                    b.setZ(z + 1);
                    ImageProcessor aIP = a.getProcessor();
                    ImageProcessor bIP = b.getProcessor();
                    for (int x = 0; x < a.getWidth(); x++)
                    {
                        for (int y = 0; y < a.getHeight(); y++)
                        {
                            if (Math.abs(aIP.getPixelValue(x, y) - bIP.getPixelValue(x, y)) > tolerance)
                            {
                                System.out.println("pixels different | " + aIP.getPixelValue(x, y) + " - " + bIP.getPixelValue(x, y) + " | > " + tolerance);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

}
