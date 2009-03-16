/*
 * Copyright (C) 2002-2007 by ?
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.MathUtils;
import org.esa.nest.gpf.OperatorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the direct (forward) orthorectification
 * method.
 *
 * The method consis of the following major steps:
 * (1) For each pixel (i,j) in the orthorectified image, get the local incidence angle alpha(i,j) and
 *     slant range distance R(i,j);
 * (2) Get local elevation h(i,j), which is computed by AddElevationBandOp from DEM given local latitude
 *     lat(i,j) and longitude lon(i,j);
 * (3) Compute ground range displacement delta(i,j) with the equation given in ADD using alpha(i,j), R(i,j)
 *     and h(i,j);
 * (4) Compoute pixel value X(i,j) = X_raw(i,j - delt(i,j)) using interpolation.
 */

@OperatorMetadata(alias="Forward-Terrain-Correction",
        description="Forward method for correcting topographic distortion caused by target elevation", internal=true)
public final class ForwardTerrainCorrectionOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    private Band sourceBand;
    private Band elevationBand;
    private MetadataElement absRoot = null;

    private TiePointGrid incidenceAngle;
    private TiePointGrid slantRangeTime;

    private int sourceImageWidth;
    private int sourceImageHeight;
    private double rangeSpacing;

    protected static final double lightSpeed = 299792458.0; //  m / s
    protected static final double halfLightSpeed = lightSpeed / 2.0;

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            boolean srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
            if (!srgrFlag) {
                throw new OperatorException("Source product should be ground detected image");
            }

            rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);

            incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);

            slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);

            elevationBand = sourceProduct.getBand("elevation");
            if (elevationBand == null) {
                throw new OperatorException("Source product does not have elevation band, please run Create Elevation Band Operator first");
            }

            createTargetProduct();

        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w  = targetTileRectangle.width;
        final int h  = targetTileRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        sourceBand = sourceProduct.getBand(targetBand.getName());
        final Tile sourceRaster = getSourceTile(sourceBand, targetTileRectangle, pm);
        final Tile elevationRaster = getSourceTile(elevationBand, targetTileRectangle, pm);
        final ProductData srcData = sourceRaster.getDataBuffer();
        final ProductData elevData = elevationRaster.getDataBuffer();
        final ProductData trgData = targetTile.getDataBuffer();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int index = sourceRaster.getDataBufferIndex(x, y);
                double v = computeOrthoRectifiedPixelValue(x, y, index, srcData, sourceRaster, elevData);
                trgData.setElemDoubleAt(index, v);
            }
        }
    }

    /**
     * Create target product.
     * @throws Exception The exception.
     */
    private void createTargetProduct() throws Exception {

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceImageWidth,
                                    sourceImageHeight);

        addSelectedBands();

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        // the tile width has to be the image width because otherwise sourceRaster.getDataBufferIndex(x, y)
        // returns incorrect index for the last tile on the right
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 20);
    }

    private void addSelectedBands() throws OperatorException {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        final Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            final String sourceBandName = sourceBandNames[i];
            final Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }

        for (Band srcBand : sourceBands) {

            if (srcBand.getName().contains("elevation")) {
                continue;
            }

            final Band targetBand = new Band(srcBand.getName(),
                                       ProductData.TYPE_FLOAT32,
                                       sourceImageWidth,
                                       sourceImageHeight);

            targetBand.setUnit(srcBand.getUnit());
            targetProduct.addBand(targetBand);
        }
    }

    private double computeOrthoRectifiedPixelValue(
            int x, int y, int index, ProductData srcData, Tile sourceRaster, ProductData elevData) {

        double slrgTime = slantRangeTime.getPixelDouble(x, y) / 1000000000.0; //convert ns to s
        double R = slrgTime * halfLightSpeed; // slant range distance in m
        double alpha = incidenceAngle.getPixelDouble(x, y) * Math.PI / 180.0; // incidence angle in radian
        double h = elevData.getElemDoubleAt(index); // target elevation in m
        double Q = R*Math.cos(alpha);
        double tmp1 = Q - h;
        double tmp2 = tmp1 / Math.cos(alpha);
        double del = (tmp1*Math.tan(alpha) - Math.sqrt(tmp2*tmp2 - Q*Q)) / rangeSpacing; // ground range displacement
        double xip = x - del; // imaged pixel position

        if (xip < 0.0 || xip >= sourceImageWidth - 1) {
            return srcData.getElemDoubleAt(index);
        }

        int x0 = (int)(xip);
        int x1 = x0 + 1;
        double mu = xip - x0;
        int index0 = sourceRaster.getDataBufferIndex(x0, y);
        int index1 = sourceRaster.getDataBufferIndex(x1, y);
        double y0 = srcData.getElemDoubleAt(index0);
        double y1 = srcData.getElemDoubleAt(index1);
        return MathUtils.interpolationLinear(y0, y1, mu);
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ForwardTerrainCorrectionOp.class);
        }
    }
}