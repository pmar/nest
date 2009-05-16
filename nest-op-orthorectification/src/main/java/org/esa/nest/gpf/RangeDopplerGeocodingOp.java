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
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.MathUtils;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.dataio.ReaderUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.File;

import Jama.Matrix;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the Range-Doppler (RD) geocoding method.
 *
 * The method consis of the following major steps:
 * (1) Get state vectors from the metadata;
 * (2) Compute satellite position and velocity for each azimuth time by interpolating the state vectors;
 * (3) Get corner latitudes and longitudes for the source image;
 * (4) Compute [LatMin, LatMax] and [LonMin, LonMax];
 * (5) Get the range and azimuth spacings for the source image;
 * (6) Compute DEM traversal sample intervals (delLat, delLon) based on source image pixel spacing;
 * (7) Compute target geocoded image dimension;
 * (8) Repeat the following steps for each sample in the target raster [LatMax:-delLat:LatMin]x[LonMin:delLon:LonMax]:
 * (8.1) Get local elevation h(i,j) for current sample given local latitude lat(i,j) and longitude lon(i,j);
 * (8.2) Convert (lat(i,j), lon(i,j), h(i,j)) to global Cartesian coordinates p(Px, Py, Pz);
 * (8.3) Compute zero Doppler time t(i,j) for point p(Px, Py, Pz) using Doppler frequency function;
 * (8.4) Compute satellite position s(i,j) and slant range r(i,j) = |s(i,j) - p| for zero Doppler time t(i,j);
 * (8.5) Compute bias-corrected zero Doppler time tc(i,j) = t(i,j) + r(i,j)*2/c, where c is the light speed;
 * (8.6) Update satellite position s(tc(i,j)) and slant range r(tc(i,j)) = |s(tc(i,j)) - p| for time tc(i,j);
 * (8.7) Compute azimuth image index Ia using zero Doppler time tc(i,j);
 * (8.8) Compute range image index Ir using slant range r(tc(i,j)) or ground range;
 * (8.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
 *
 * Reference: Guide to ASAR Geocoding, Issue 1.0, 19.03.2008
 */

@OperatorMetadata(alias="Range-Doppler-Geocoding", description="RD method for orthorectification")
public final class RangeDopplerGeocodingOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames = null;

    @Parameter(valueSet = {"ACE", "GETASSE30", "SRTM 3Sec GeoTiff"}, description = "The digital elevation model.",
               defaultValue="SRTM 3Sec GeoTiff", label="Digital Elevation Model")
    private String demName = "SRTM 3Sec GeoTiff";


    @Parameter(label="External DEM")
    private File externalDemFile = null;

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="DEM Resampling Method")
    private String demResamplingMethod = BILINEAR;

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="Image Resampling Method")
    private String imgResamplingMethod = BILINEAR;

    @Parameter(defaultValue="false", label="Save DEM as band")
    private boolean saveDEM = false;

    private MetadataElement absRoot = null;
    private ElevationModel dem = null;
    private FileElevationModel fileElevationModel = null;
    private boolean srgrFlag = false;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int targetImageWidth = 0;
    private int targetImageHeight = 0;

    private double wavelength = 0.0; // in m
    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private double demNoDataValue = 0.0; // no data value for DEM
    private double latMin = 0.0;
    private double latMax = 0.0;
    private double lonMin = 0.0;
    private double lonMax= 0.0;
    private double delLat = 0.0;
    private double delLon = 0.0;

    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] timeArray = null;
    private double[] xPosArray = null;
    private double[] yPosArray = null;
    private double[] zPosArray = null;

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();

    private static final String NEAREST_NEIGHBOUR = "Nearest Neighbour";
    private static final String BILINEAR = "Bilinear Interpolation";
    private static final String CUBIC = "Cubic Convolution";
    private static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)
    private static final double NonValidZeroDopplerTime = -99999.0;

    private enum ResampleMethod { RESAMPLE_NEAREST_NEIGHBOUR, RESAMPLE_BILINEAR, RESAMPLE_CUBIC }
    private ResampleMethod imgResampling = null;

    private Map<String, ArrayList<Tile>> tileCache = new HashMap<String, ArrayList<Tile>>(2);

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

            getSRGRFlag();

            getRadarFrequency();

            getRangeAzimuthSpacings();

            getFirstLastLineTimes();

            getLineTimeInterval();

            getOrbitStateVectors();

            if (srgrFlag) {
                getSrgrCoeff();
            } else {
                getNearEdgeSlantRange();
            }

            computeImageGeoBoundary();

            computeDEMTraversalSampleInterval();

            computedTargetImageDimension();

            getElevationModel();

            getSourceImageDimension();

            createTargetProduct();

            computeSensorPositionsAndVelocities();

            if (imgResamplingMethod.equals(NEAREST_NEIGHBOUR)) {
                imgResampling = ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR;
            } else if (imgResamplingMethod.contains(BILINEAR)) {
                imgResampling = ResampleMethod.RESAMPLE_BILINEAR;
            } else if (imgResamplingMethod.contains(CUBIC)) {
                imgResampling = ResampleMethod.RESAMPLE_CUBIC;
            } else {
                throw new OperatorException("Unknown interpolation method");
            }

        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get SRGR flag from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getSRGRFlag() throws Exception {
        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
    }

    /**
     * Get radar frequency from the abstracted metadata (in Hz).
     * @throws Exception The exceptions.
     */
    private void getRadarFrequency() throws Exception {
        final double radarFreq = AbstractMetadata.getAttributeDouble(absRoot,
                                                    AbstractMetadata.radar_frequency)*Constants.oneMillion; // Hz
        wavelength = Constants.lightSpeed / radarFreq;
    }

    /**
     * Get range and azimuth spacings from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getRangeAzimuthSpacings() throws Exception {
        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
    }

    /**
     * Get orbit state vectors from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getOrbitStateVectors() throws Exception {
        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
    }

    /**
     * Get SRGR conversion parameters.
     * @throws Exception The exceptions.
     */
    private void getSrgrCoeff() throws Exception {
        srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
    }

    /**
     * Get near edge slant range (in m).
     * @throws Exception The exceptions.
     */
    private void getNearEdgeSlantRange() throws Exception {
        nearEdgeSlantRange = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.slant_range_to_first_pixel);
    }

    /**
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     * @throws Exception The exceptions.
     */
    private void computeImageGeoBoundary() throws Exception {
        
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0,0), null);
        final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(sourceProduct.getSceneRasterWidth(),0), null);
        final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(0,sourceProduct.getSceneRasterHeight()), null);
        final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(sourceProduct.getSceneRasterWidth(),
                                                                      sourceProduct.getSceneRasterHeight()), null);
        
        final double[] lats  = {geoPosFirstNear.getLat(), geoPosFirstFar.getLat(), geoPosLastNear.getLat(), geoPosLastFar.getLat()};
        final double[] lons  = {geoPosFirstNear.getLon(), geoPosFirstFar.getLon(), geoPosLastNear.getLon(), geoPosLastFar.getLon()};
        latMin = 90.0;
        latMax = -90.0;
        for (double lat : lats) {
            if (lat < latMin) {
                latMin = lat;
            }
            if (lat > latMax) {
                latMax = lat;
            }
        }

        lonMin = 180.0;
        lonMax = -180.0;
        for (double lon : lons) {
            if (lon < lonMin) {
                lonMin = lon;
            }
            if (lon > lonMax) {
                lonMax = lon;
            }
        }
    }

    /**
     * Compute DEM traversal step sizes (in degree) in latitude and longitude.
     */
    private void computeDEMTraversalSampleInterval() {

        final double minSpacing = Math.min(rangeSpacing, azimuthSpacing);
        double minAbsLat;
        if (latMin*latMax > 0) {
            minAbsLat = Math.min(Math.abs(latMin), Math.abs(latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
        } else {
            minAbsLat = 0.0;
        }
        delLat = minSpacing / MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        delLon = minSpacing / (MeanEarthRadius*Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
        delLat = Math.min(delLat, delLon);
        delLon = delLat;
    }

    /**
     * Compute target image dimension.
     */
    private void computedTargetImageDimension() {
        targetImageWidth = (int)((lonMax - lonMin)/delLon) + 1;
        targetImageHeight = (int)((latMax - latMin)/delLat) + 1;
    }

    /**
     * Get first line time from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getFirstLastLineTimes() throws Exception {
        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
        lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD(); // in days
    }

    /**
     * Get line time interval from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getLineTimeInterval() throws Exception {
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
    }

    /**
     * Get elevation model.
     */
    private void getElevationModel() {

        final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
        final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
        if (demDescriptor == null) {
            throw new OperatorException("The DEM '" + demName + "' is not supported.");
        }

        if (demDescriptor.isInstallingDem()) {
            throw new OperatorException("The DEM '" + demName + "' is currently being installed.");
        }

        dem = demDescriptor.createDem(getResamplingMethod());
        if(dem == null) {
            throw new OperatorException("The DEM '" + demName + "' has not been installed.");
        }

        demNoDataValue = dem.getDescriptor().getNoDataValue();
    }

    private Resampling getResamplingMethod() {
        Resampling resamplingMethod = Resampling.BILINEAR_INTERPOLATION;
        if(demResamplingMethod.equals(NEAREST_NEIGHBOUR)) {
            resamplingMethod = Resampling.NEAREST_NEIGHBOUR;
        } else if(demResamplingMethod.equals(BILINEAR)) {
            resamplingMethod = Resampling.BILINEAR_INTERPOLATION;
        } else if(demResamplingMethod.equals(CUBIC)) {
            resamplingMethod = Resampling.CUBIC_CONVOLUTION;
        }
        return resamplingMethod;
    }

    /**
     * Get source image width and height.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Create target product.
     * @throws OperatorException The exception.
     */
    private void createTargetProduct() throws OperatorException {
        
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        addSelectedBands();

        addGeoCoding();

        updateTargetProductMetadata();

        // the tile width has to be the image width because otherwise sourceRaster.getDataBufferIndex(x, y)
        // returns incorrect index for the last tile on the right
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 20);
    }

    /**
     * Add the user selected bands to target product.
     * @throws OperatorException The exceptions.
     */
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

        String targetBandName;
        for (int i = 0; i < sourceBands.length; i++) {

            final Band srcBand = sourceBands[i];
            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band " + srcBand.getName() + " requires a unit");
            }

            String targetUnit = "";

            if (unit.contains(Unit.PHASE)) {

                continue;

            } else if (unit.contains(Unit.IMAGINARY)) {

                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit.contains(Unit.REAL)) {

                if (i == sourceBands.length - 1) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.contains(Unit.IMAGINARY)) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Intensity_" + pol.toUpperCase();
                } else {
                    targetBandName = "Intensity";
                }
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = Unit.INTENSITY;
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = srcBand.getName();
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = unit;
                }
            }

            if(targetProduct.getBand(targetBandName) == null) {

                final Band targetBand = new Band(targetBandName,
                                                 srcBand.getDataType(),
                                                 targetImageWidth,
                                                 targetImageHeight);

                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }

        if(saveDEM) {
            final Band demBand = new Band("elevation",
                                             ProductData.TYPE_INT32,
                                             targetImageWidth,
                                             targetImageHeight);
            demBand.setUnit(Unit.METERS);
            targetProduct.addBand(demBand);
        }
    }

    /**
     * Add geocoding to the target product.
     */
    private void addGeoCoding() {

        final float[] latTiePoints = {(float)latMax, (float)latMax, (float)latMin, (float)latMin};
        final float[] lonTiePoints = {(float)lonMin, (float)lonMax, (float)lonMin, (float)lonMax};

        final int gridWidth = 10;
        final int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latTiePoints, fineLatTiePoints);

        float subSamplingX = (float)targetImageWidth / (gridWidth - 1);
        float subSamplingY = (float)targetImageHeight / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonTiePoints, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);
    }

    /**
     * Update metadata in the target product.
     * @throws OperatorException The exception.
     */
    private void updateTargetProductMetadata() throws OperatorException {

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.srgr_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_output_lines, targetImageHeight);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_samples_per_line, targetImageWidth);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_near_lat, latMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_far_lat, latMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_near_lat, latMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_far_lat, latMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_near_long, lonMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_far_long, lonMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_near_long, lonMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_far_long, lonMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.TOT_SIZE,
                (int)(targetProduct.getRawStorageSize() / (1024.0f * 1024.0f)));
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.is_geocoded, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.geo_ref_system, "WGS84");
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lat_pixel_res, delLat);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lon_pixel_res, delLon);
    }

    /**
     * Compute sensor position and velocity for each range line from the orbit state vectors using
     * cubic WARP polynomial.
     */
    private void computeSensorPositionsAndVelocities() {

        final int numVectors = orbitStateVectors.length;
        final int numVectorsUsed = Math.min(numVectors, 5);
        final int d = numVectors / numVectorsUsed;

        timeArray = new double[numVectorsUsed];
        xPosArray = new double[numVectorsUsed];
        yPosArray = new double[numVectorsUsed];
        zPosArray = new double[numVectorsUsed];
        final double[] xVelArray = new double[numVectorsUsed];
        final double[] yVelArray = new double[numVectorsUsed];
        final double[] zVelArray = new double[numVectorsUsed];

        for (int i = 0; i < numVectorsUsed; i++) {
            timeArray[i] = orbitStateVectors[i*d].time.getMJD();
            xPosArray[i] = orbitStateVectors[i*d].x_pos; // m
            yPosArray[i] = orbitStateVectors[i*d].y_pos; // m
            zPosArray[i] = orbitStateVectors[i*d].z_pos; // m
            xVelArray[i] = orbitStateVectors[i*d].x_vel; // m/s
            yVelArray[i] = orbitStateVectors[i*d].y_vel; // m/s
            zVelArray[i] = orbitStateVectors[i*d].z_vel; // m/s
        }

        // Lagrange polynomial interpolation
        sensorPosition = new double[sourceImageHeight][3]; // xPos, yPos, zPos
        sensorVelocity = new double[sourceImageHeight][3]; // xVel, yVel, zVel
        for (int i = 0; i < sourceImageHeight; i++) {
            final double time = firstLineUTC + i*lineTimeInterval; // zero Doppler time (in days) for each range line
            sensorPosition[i][0] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, xPosArray, time);
            sensorPosition[i][1] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, yPosArray, time);
            sensorPosition[i][2] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, zPosArray, time);
            sensorVelocity[i][0] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, xVelArray, time);
            sensorVelocity[i][1] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, yVelArray, time);
            sensorVelocity[i][2] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, zVelArray, time);
        }
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        /*
        * (8.1) Get local elevation h(i,j) for current sample given local latitude lat(i,j) and longitude lon(i,j);
        * (8.2) Convert (lat(i,j), lon(i,j), h(i,j)) to global Cartesian coordinates p(Px, Py, Pz);
        * (8.3) Compute zero Doppler time t(i,j) for point p(Px, Py, Pz) using Doppler frequency function;
        * (8.4) Compute satellite position s(i,j) and slant range r(i,j) = |s(i,j) - p| for zero Doppler time t(i,j);
        * (8.5) Compute bias-corrected zero Doppler time tc(i,j) = t(i,j) + r(i,j)*2/c, where c is the light speed;
        * (8.6) Update satellite position s(tc(i,j)) and slant range r(tc(i,j)) = |s(tc(i,j)) - p| for time tc(i,j);
        * (8.7) Compute azimuth image index Ia using zero Doppler time tc(i,j);
        * (8.8) Compute range image index Ir using slant range r(tc(i,j)) or groung range;
        * (8.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
        */
        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w  = targetRectangle.width;
        final int h  = targetRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        final GeoPos geoPos = new GeoPos();
        final double[] earthPoint = new double[3];
        final int srcMaxRange = sourceImageWidth - 1;
        final int srcMaxAzimuth = sourceImageHeight - 1;
        ProductData demBuffer = null;

        final ArrayList<TileData> trgTileList = new ArrayList<TileData>();
        final Set<Band> keySet = targetTiles.keySet();
        for(Band targetBand : keySet) {
            if(targetBand.getName().equals("elevation")) {
                demBuffer = targetTiles.get(targetBand).getDataBuffer();
                continue;
            }
            final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());

            final TileData td = new TileData();
            td.targetTile = targetTiles.get(targetBand);
            td.tileDataBuffer = td.targetTile.getDataBuffer();
            td.bandName = targetBand.getName();
            td.noDataValue = sourceProduct.getBand(srcBandNames[0]).getNoDataValue();
            trgTileList.add(td);
        }
        final TileData[] trgTiles = trgTileList.toArray(new TileData[trgTileList.size()]);

        try {
            for (int y = y0; y < y0 + h; y++) {
                final double lat = latMax - y*delLat;

                for (int x = x0; x < x0 + w; x++) {
                    final int index = trgTiles[0].targetTile.getDataBufferIndex(x, y);

                    final double lon = lonMin + x*delLon;
                    geoPos.setLocation((float)lat, (float)lon);
                    final double alt = getLocalElevation(geoPos);
                    if(saveDEM) {
                        demBuffer.setElemDoubleAt(index, alt);
                    }

                    GeoUtils.geo2xyz(lat, lon, alt, earthPoint);
                    final double zeroDopplerTime = getEarthPointZeroDopplerTime(earthPoint);
                    if (Double.compare(zeroDopplerTime, NonValidZeroDopplerTime) == 0) {
                        for(TileData tileData : trgTiles) {
                            tileData.tileDataBuffer.setElemDoubleAt(index, tileData.noDataValue);
                        }
                        continue;
                    }

                    double slantRange = computeSlantRangeDistance(earthPoint, zeroDopplerTime);
                    final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / Constants.halfLightSpeed / 86400.0;
                    final double azimuthIndex = (zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval;
                    slantRange = computeSlantRangeDistance(earthPoint, zeroDopplerTimeWithoutBias);

                    final double rangeIndex = computeRangeIndex(zeroDopplerTimeWithoutBias, slantRange);
                    if (rangeIndex < 0.0 || rangeIndex >= srcMaxRange ||
                            azimuthIndex < 0.0 || azimuthIndex >= srcMaxAzimuth) {
                        for(TileData tileData : trgTiles) {
                            tileData.tileDataBuffer.setElemDoubleAt(index, tileData.noDataValue);
                        }
                    } else {
                        for(TileData tileData : trgTiles) {
                            tileData.tileDataBuffer.setElemDoubleAt(index, getPixelValue(azimuthIndex, rangeIndex, tileData.bandName));
                        }
                    }
                }
            }
        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get local elevation (in meter) for given latitude and longitude.
     * @param geoPos The latitude and longitude in degrees.
     * @return The elevation in meter.
     */
    private double getLocalElevation(final GeoPos geoPos) {
        double alt;
        try {
            if(externalDemFile == null) {
                alt = dem.getElevation(geoPos);
            } else {
                if(fileElevationModel == null) {
                    fileElevationModel = new FileElevationModel(externalDemFile,
                            getResamplingMethod());
                }
                alt = fileElevationModel.getElevation(geoPos);
            }
        } catch (Exception e) {
            alt = demNoDataValue;
        }
        return alt == demNoDataValue ? 0.0 : alt;
    }

    /**
     * Compute zero Doppler time for given erath point.
     * @param earthPoint The earth point in xyz cooordinate.
     * @return The zero Doppler time in days if it is found, -1 otherwise.
     * @throws OperatorException The operator exception.
     */
    private double getEarthPointZeroDopplerTime(final double[] earthPoint) throws OperatorException {

        // binary search is used in finding the zero doppler time
        int lowerBound = 0;
        int upperBound = sensorPosition.length - 1;
        double lowerBoundFreq = getDopplerFrequency(lowerBound, earthPoint);
        double upperBoundFreq = getDopplerFrequency(upperBound, earthPoint);

        if (Double.compare(lowerBoundFreq, 0.0) == 0) {
            return firstLineUTC + lowerBound*lineTimeInterval;
        } else if (Double.compare(upperBoundFreq, 0.0) == 0) {
            return firstLineUTC + upperBound*lineTimeInterval;
        } else if (lowerBoundFreq*upperBoundFreq > 0.0) {
            return NonValidZeroDopplerTime;
        }

        // start binary search
        double midFreq;
        while(upperBound - lowerBound > 1) {

            final int mid = (int)((lowerBound + upperBound)/2.0);
            midFreq = getDopplerFrequency(mid, earthPoint);
            if (Double.compare(midFreq, 0.0) == 0) {
                return firstLineUTC + mid*lineTimeInterval;
            } else if (midFreq*lowerBoundFreq > 0.0) {
                lowerBound = mid;
                lowerBoundFreq = midFreq;
            } else if (midFreq*upperBoundFreq > 0.0) {
                upperBound = mid;
                upperBoundFreq = midFreq;
            }
        }

        final double y0 = lowerBound - lowerBoundFreq*(upperBound - lowerBound)/(upperBoundFreq - lowerBoundFreq);
        return firstLineUTC + y0*lineTimeInterval;
    }

    /**
     * Compute Doppler frequency for given earthPoint and sensor position.
     * @param y The index for given range line.
     * @param earthPoint The earth point in xyz coordinate.
     * @return The Doppler frequency in Hz.
     */
    private double getDopplerFrequency(final int y, final double[] earthPoint) {

        if (y < 0 || y > sourceImageHeight - 1) {
            throw new OperatorException("Invalid range line index: " + y);
        }
        
        final double xVel = sensorVelocity[y][0];
        final double yVel = sensorVelocity[y][1];
        final double zVel = sensorVelocity[y][2];
        final double xDiff = earthPoint[0] - sensorPosition[y][0];
        final double yDiff = earthPoint[1] - sensorPosition[y][1];
        final double zDiff = earthPoint[2] - sensorPosition[y][2];
        final double distance = Math.sqrt(xDiff*xDiff + yDiff*yDiff + zDiff*zDiff);

        return 2.0 * (xVel*xDiff + yVel*yDiff + zVel*zDiff) / (distance*wavelength);
    }

    /**
     * Compute slant range distance for given earth point and given time.
     * @param earthPoint The earth point in xyz coordinate.
     * @param time The given time in days.
     * @return The slant range distance in meters.
     */
    private double computeSlantRangeDistance(final double[] earthPoint, final double time) {

        final double sensorXPos = MathUtils.lagrangeInterpolatingPolynomial(timeArray, xPosArray, time);
        final double sensorYPos = MathUtils.lagrangeInterpolatingPolynomial(timeArray, yPosArray, time);
        final double sensorZPos = MathUtils.lagrangeInterpolatingPolynomial(timeArray, zPosArray, time);

        final double xDiff = sensorXPos - earthPoint[0];
        final double yDiff = sensorYPos - earthPoint[1];
        final double zDiff = sensorZPos - earthPoint[2];

        return Math.sqrt(xDiff*xDiff + yDiff*yDiff + zDiff*zDiff);
    }

    /**
     * Compute range index in source image for earth point with given zero Doppler time and slant range.
     * @param zeroDopplerTime The zero Doppler time in MJD.
     * @param slantRange The slant range in meters.
     * @return The range index.
     */
    private double computeRangeIndex(final double zeroDopplerTime, final double slantRange) {

        if (zeroDopplerTime < firstLineUTC || zeroDopplerTime > lastLineUTC) {
            return -1.0;
        }

        if (srgrFlag) { // ground detected image

            int idx = 0;
            for (int i = 0; i < srgrConvParams.length && zeroDopplerTime >= srgrConvParams[i].time.getMJD(); i++) {
                idx = i;
            }
            final double groundRange = computeGroundRange(slantRange, srgrConvParams[idx].coefficients);
            return (groundRange - srgrConvParams[idx].ground_range_origin) / rangeSpacing;

        } else { // slant range image

            return (slantRange - nearEdgeSlantRange) / rangeSpacing;
        }
    }

    /**
     * Compute ground range for given slant range.
     * @param slantRange The salnt range in meters.
     * @param srgrCoeff The SRGR coefficients for converting ground range to slant range.
     *                  Here it is assumed that the polinomial is given by
     *                  c0 + c1*x + c2*x^2 + ... + cn*x^n, where {c0, c1, ..., cn} are the SRGR coefficients.
     * @return The ground range in meters.
     */
    private static double computeGroundRange(final double slantRange, final double[] srgrCoeff) {

        // todo Can Newton's method be uaed in find zeros for the 4th order polynomial?
        // todo Note for ASAR product here computes (Gr - Gr0), not Gr. Therefore not Gr0 should be subtracted later in computing range index.
        double x = slantRange;
        double y = computePolinomialValue(x, srgrCoeff) - slantRange;
        while (Math.abs(y) > 0.0001) {
            final double derivative = computePolinomialDerivativeValue(x, srgrCoeff);
            x -= y / derivative;
            y = computePolinomialValue(x, srgrCoeff) - slantRange;
        }
        return x;
    }

    /**
     * Compute polynomial value.
     * @param x The variable.
     * @param srgrCoeff The polynomial coefficients.
     * @return The function value.
     */
    private static double computePolinomialValue(final double x, final double[] srgrCoeff) {
        double v = 0.0;
        for (int i = srgrCoeff.length-1; i > 0; i--) {
            v = (v + srgrCoeff[i])*x;
        }
        return v + srgrCoeff[0];
    }

    /**
     * Compute polynomial derivative value.
     * @param x The variable.
     * @param srgrCoeff The polynomial coefficients.
     * @return The function derivative value.
     */
    private static double computePolinomialDerivativeValue(final double x, final double[] srgrCoeff) {
        double v = 0.0;
        for (int i = srgrCoeff.length-1; i > 1; i--) {
            v = (v + i*srgrCoeff[i])*x;
        }
        return v + srgrCoeff[1];
    }

    /**
     * Compute orthorectified pixel value for given pixel.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandName the name of the target band
     * @return The pixel value.
     * @throws IOException from readPixels
     */
    private double getPixelValue(final double azimuthIndex, final double rangeIndex, final String bandName) throws IOException {

        final String[] srcBandNames = targetBandNameToSourceBandName.get(bandName);
        final String iBandName = srcBandNames[0];
        String qBandName = null;
        if (srcBandNames.length > 1) {
            qBandName = srcBandNames[1];
        }

        if (imgResampling.equals(ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR)) {
            final Tile sourceTile = getSrcTile(iBandName, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            final Tile sourceTile2 = getSrcTile(qBandName, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            return getPixelValueUsingNearestNeighbourInterp(azimuthIndex, rangeIndex, sourceTile, sourceTile2);
        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_BILINEAR)) {
            final Tile sourceTile = getSrcTile(iBandName, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            final Tile sourceTile2 = getSrcTile(qBandName, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            return getPixelValueUsingBilinearInterp(azimuthIndex, rangeIndex, sourceImageWidth, sourceImageHeight,
                                                    sourceTile, sourceTile2);
        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_CUBIC)) {
            final Tile sourceTile = getSrcTile(iBandName, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            final Tile sourceTile2 = getSrcTile(qBandName, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            return getPixelValueUsingBicubicInterp(azimuthIndex, rangeIndex, sourceImageWidth, sourceImageHeight,
                                                    sourceTile, sourceTile2);
        } else {
            throw new OperatorException("Unknown interpolation method");
        }
    }

    private Tile getSrcTile(String bandName, int minX, int minY, int width, int height) {
        if(bandName == null)
            return null;

        final Band sourceBand = sourceProduct.getBand(bandName);
        final Rectangle srcRect = new Rectangle(minX, minY, width, height);
        return getSourceTile(sourceBand, srcRect, ProgressMonitor.NULL);
    }

    /*
    private Tile getSrcTile(String bandName, int minX, int minY, int width, int height) {
        if(bandName == null)
            return null;
        
        ArrayList<Tile> srcTiles = tileCache.get(bandName);
        if(srcTiles == null) {
            srcTiles = new ArrayList<Tile>();
            tileCache.put(bandName, srcTiles);
        }
        for(Tile t : srcTiles) {
            if(minX >= t.getMinX() && minY >= t.getMinY() &&
               (minX+width) < t.getMaxX() && (minY+height) < t.getMaxY()) {
                return t;
            }
        }

        final int tileSize = 1024;
        final Band sourceBand = sourceProduct.getBand(bandName);
        final int x = Math.max(0,minX-tileSize/2);
        final int y = Math.max(0, minY-tileSize/2);
        final Rectangle srcRect = new Rectangle(x, y,
                Math.min(tileSize, sourceImageWidth-x), Math.min(tileSize, sourceImageHeight-y));
        final Tile sourceTile = getSourceTile(sourceBand, srcRect, ProgressMonitor.NULL);

        srcTiles.clear();
        srcTiles.add(sourceTile);

        //if(srcTiles.size() > 3) {
        //    srcTiles.remove(0);
        //}

        return sourceTile;
    }     */

    /**
     * Get source image pixel value using nearest neighbot interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param sourceTile  i
     * @param sourceTile2 q
     * @return The pixel value.
     */
    private static double getPixelValueUsingNearestNeighbourInterp(final double azimuthIndex, final double rangeIndex,
                                                                   final Tile sourceTile, final Tile sourceTile2) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;

        if (sourceTile2 == null) {
            return sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
        } else {
            final double vi = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vq = sourceTile2.getDataBuffer().getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            return vi*vi + vq*vq;
        }
    }

    /**
     * Get source image pixel value using bilinear interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @return The pixel value.
     */
    private static double getPixelValueUsingBilinearInterp(final double azimuthIndex, final double rangeIndex,
                                                           final int sceneRasterWidth, final int sceneRasterHeight,
                                                           final Tile sourceTile, final Tile sourceTile2) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;
        final int x1 = Math.min(x0 + 1, sceneRasterWidth - 1);
        final int y1 = Math.min(y0 + 1, sceneRasterHeight - 1);

        final ProductData srcData = sourceTile.getDataBuffer();

        final double v00, v01, v10, v11;
        if (sourceTile2 == null) {
            v00 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            v01 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y0));
            v10 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y1));
            v11 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y1));
        } else {
            final ProductData srcData2 = sourceTile2.getDataBuffer();

            final double vi00 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vi01 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y0));
            final double vi10 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y1));
            final double vi11 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y1));

            final double vq00 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            final double vq01 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x1, y0));
            final double vq10 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y1));
            final double vq11 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x1, y1));

            v00 = vi00*vi00 + vq00*vq00;
            v01 = vi01*vi01 + vq01*vq01;
            v10 = vi10*vi10 + vq10*vq10;
            v11 = vi11*vi11 + vq11*vq11;
        }
        return MathUtils.interpolationBiLinear(v00, v01, v10, v11, rangeIndex - x0, azimuthIndex - y0);
    }

    /**
     * Get source image pixel value using bicubic interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @return The pixel value.
     */
    private static double getPixelValueUsingBicubicInterp(final double azimuthIndex, final double rangeIndex,
                                                          final int sceneRasterWidth, final int sceneRasterHeight,
                                                          final Tile sourceTile, final Tile sourceTile2) {

        final int [] x = new int[4];
        x[1] = (int)rangeIndex;
        x[0] = Math.max(0, x[1] - 1);
        x[2] = Math.min(x[1] + 1, sceneRasterWidth - 1);
        x[3] = Math.min(x[1] + 2, sceneRasterWidth - 1);

        final int [] y = new int[4];
        y[1] = (int)azimuthIndex;
        y[0] = Math.max(0, y[1] - 1);
        y[2] = Math.min(y[1] + 1, sceneRasterHeight - 1);
        y[3] = Math.min(y[1] + 2, sceneRasterHeight - 1);

        final ProductData srcData = sourceTile.getDataBuffer();

        final double[][] v = new double[4][4];
        if(sourceTile2 == null) {
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
                }
            }
        } else {

            final ProductData srcData2 = sourceTile2.getDataBuffer();
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    final double vi = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
                    final double vq = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x[j], y[i]));
                    v[i][j] = vi*vi + vq*vq;
                }
            }
        }

        return MathUtils.interpolationBiCubic(v, rangeIndex - x[1], azimuthIndex - y[1]);
    }

    private static class TileData {
        Tile targetTile = null;
        ProductData tileDataBuffer = null;
        String bandName = null;
        double noDataValue = 0;
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
            super(RangeDopplerGeocodingOp.class);
        }
    }
}