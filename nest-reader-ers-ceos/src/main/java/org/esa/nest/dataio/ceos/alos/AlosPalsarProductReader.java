package org.esa.nest.dataio.ceos.alos;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CEOSProductReader;

import java.io.File;
import java.io.IOException;

/**
 * The product reader for AlosPalsar products.
 *
 */
public class AlosPalsarProductReader extends CEOSProductReader {

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public AlosPalsarProductReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    @Override
    protected CEOSProductDirectory createProductDirectory(File inputFile) throws IOException, IllegalBinaryFormatException {
        return new AlosPalsarProductDirectory(inputFile.getParentFile());
    }

    DecodeQualification checkProductQualification(File file) {

        try {
            _dataDir = createProductDirectory(file);

            final AlosPalsarProductDirectory ersDataDir = (AlosPalsarProductDirectory)_dataDir;
            if(ersDataDir.isALOS())
                return DecodeQualification.INTENDED;
            return DecodeQualification.SUITABLE;

        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {
        try {
            final AlosPalsarProductDirectory dataDir = (AlosPalsarProductDirectory) _dataDir;
            final AlosPalsarImageFile imageFile = (AlosPalsarImageFile)dataDir.getImageFile(destBand);
            if(dataDir.isSLC()) {
                boolean oneOf2 = !destBand.getName().startsWith("q");

                if(dataDir.getProductLevel() == AlosPalsarConstants.LEVEL1_0) {
                    imageFile.readBandRasterDataSLCByte(sourceOffsetX, sourceOffsetY,
                                         sourceWidth, sourceHeight,
                                         sourceStepX, sourceStepY,
                                         destWidth,
                                         destBuffer, oneOf2, pm);
                } else {
                    imageFile.readBandRasterDataSLCFloat(sourceOffsetX, sourceOffsetY,
                                         sourceWidth, sourceHeight,
                                         sourceStepX, sourceStepY,
                                         destWidth,
                                         destBuffer, oneOf2, pm);
                }
            } else {
                imageFile.readBandRasterDataShort(sourceOffsetX, sourceOffsetY,
                                         sourceWidth, sourceHeight,
                                         sourceStepX, sourceStepY,
                                         destWidth,
                                         destBuffer, pm);
            }

        } catch (Exception e) {
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }

    }

}