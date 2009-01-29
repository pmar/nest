
package org.esa.nest.dataio;

import org.esa.beam.framework.dataio.ProductWriter;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.util.io.BeamFileFilter;

import java.io.File;
import java.util.Locale;


public class ImageIOWriterPlugIn implements ProductWriterPlugIn {

    public static final String FORMAT_NAME = "ImageIO";

    /**
     * Constructs a new product writer plug-in instance.
     */
    public ImageIOWriterPlugIn() {
    }

    public String[] getFormatNames() {
        return new String[]{FORMAT_NAME};
    }

    public String[] getDefaultFileExtensions() {
        return null;
    }

    /**
     * Returns an array containing the classes that represent valid output types for this GDAL product writer.
     * <p/>
     * <p> Intances of the classes returned in this array are valid objects for the <code>writeProductNodes</code>
     * method of the <code>AbstractProductWriter</code> interface (the method will not throw an
     * <code>InvalidArgumentException</code> in this case).
     *
     * @return an array containing valid output types, never <code>null</code>
     *
     * @see org.esa.beam.framework.dataio.AbstractProductWriter#writeProductNodes
     */
    public Class[] getOutputTypes() {
        return new Class[]{
                String.class,
                File.class,
//            ImageOutputStream.class
        };
    }

    /**
     * Gets a short description of this plug-in. If the given locale is set to <code>null</code> the default locale is
     * used.
     * <p/>
     * <p> In a GUI, the description returned could be used as tool-tip text.
     *
     * @param name the local for the given decription string, if <code>null</code> the default locale is used
     *
     * @return a textual description of this product reader/writer
     */
    public String getDescription(Locale name) {
        return "ImageIO writer";
    }

    /**
     * Creates an instance of the actual product writer class.
     *
     * @return a new instance of the <code>GDALWriter</code> class
     */
    public ProductWriter createWriterInstance() {
        return new ImageIOWriter(this, "jpeg2000"); 
    }

    public BeamFileFilter getProductFileFilter() {
        return new BeamFileFilter(getFormatNames()[0], getDefaultFileExtensions(), getDescription(null));
    }
}