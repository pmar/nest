package org.esa.nest.gpf;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StringUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.dataio.ReaderUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Helper methods for working with Operators
 */
public final class OperatorUtils {

    public static final String TPG_SLANT_RANGE_TIME = "slant_range_time";
    public static final String TPG_INCIDENT_ANGLE = "incident_angle";
    public static final String TPG_LATITUDE = "latitude";
    public static final String TPG_LONGITUDE = "longitude";

    /**
     * Get incidence angle tie point grid.
     * @param sourceProduct The source product.
     * @param tiePointGridName The tie point grid name.
     * @return srcTPG The incidence angle tie point grid.
     */
    private static TiePointGrid getTiePointGrid(final Product sourceProduct, final String tiePointGridName) {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals(tiePointGridName)) {
                return srcTPG;
            }
        }

        return null;
    }

    /**
     * Get incidence angle tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The incidence angle tie point grid.
     */
    public static TiePointGrid getIncidenceAngle(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_INCIDENT_ANGLE);
    }

    /**
     * Get slant range time tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The slant range time tie point grid.
     */
    public static TiePointGrid getSlantRangeTime(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_SLANT_RANGE_TIME);
    }

    /**
     * Get latitude tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The latitude tie point grid.
     */
    public static TiePointGrid getLatitude(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_LATITUDE);
    }

    /**
     * Get longitude tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The longitude tie point grid.
     */
    public static TiePointGrid getLongitude(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_LONGITUDE);
    }

    public static String getBandPolarization(final String bandName, final MetadataElement absRoot) {
        final String pol = OperatorUtils.getPolarizationFromBandName(bandName);
        if (pol != null) {
            return pol;
        } else {
            final String[] mdsPolar = getProductPolarization(absRoot);
            return mdsPolar[0];
        }
    }

    private static String getPolarizationFromBandName(final String bandName) {

        final int idx = bandName.lastIndexOf('_');
        if (idx != -1) {
            final String pol = bandName.substring(idx+1).toLowerCase();
            if (pol.contains("hh") || pol.contains("vv") || pol.contains("hv") || pol.contains("vh")) {
                return pol;
            } 
        }
        return null;
    }

    /**
     * Get product polarizations for each band in the product.
     * @param absRoot the AbstractMetadata
     * @return mdsPolar the string array to hold the polarization names
     */
    public static String[] getProductPolarization(final MetadataElement absRoot) {

        final String[] mdsPolar = new String[4];
        for(int i=0; i < mdsPolar.length; ++i) {
            final String polarName = absRoot.getAttributeString(AbstractMetadata.polarTags[i], "").toLowerCase();
            mdsPolar[i] = "";
            if (polarName.contains("hh") || polarName.contains("hv") || polarName.contains("vh") || polarName.contains("vv")) {
                mdsPolar[i] = polarName;
            }
        }
        return mdsPolar;
    }

    public static String getSuffixFromBandName(final String bandName) {

        final int idx1 = bandName.lastIndexOf('_');
        if (idx1 != -1) {
            return bandName.substring(idx1+1).toLowerCase();
        }
        final int idx2 = bandName.lastIndexOf('-');
        if (idx2 != -1) {
            return bandName.substring(idx2+1).toLowerCase();
        }
        final int idx3 = bandName.lastIndexOf('.');
        if (idx3 != -1) {
            return bandName.substring(idx3+1).toLowerCase();
        }
        return null;
    }

    public static void copyProductNodes(final Product sourceProduct, final Product targetProduct) {
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        targetProduct.setDescription(sourceProduct.getDescription());
    }

    public static void copyVirtualBand(final Product product, final VirtualBand srcBand, final String name) {

        final VirtualBand virtBand = new VirtualBand(name,
                srcBand.getDataType(),
                srcBand.getSceneRasterWidth(),
                srcBand.getSceneRasterHeight(),
                srcBand.getExpression());
        virtBand.setSynthetic(true);
        virtBand.setUnit(srcBand.getUnit());
        virtBand.setDescription(srcBand.getDescription());
        virtBand.setNoDataValue(srcBand.getNoDataValue());
        virtBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
        product.addBand(virtBand);
    }

    public static boolean isDIMAP(Product prod) {
        return StringUtils.contains(prod.getProductReader().getReaderPlugIn().getFormatNames(),
                                    DimapProductConstants.DIMAP_FORMAT_NAME);
    }

    public static boolean isMapProjected(Product product) {
        if(product.getGeoCoding() instanceof MapGeoCoding || product.getGeoCoding() instanceof CrsGeoCoding)
            return true;
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        return absRoot != null && !absRoot.getAttributeString(AbstractMetadata.map_projection, "").trim().isEmpty();
    }

    /**
     * Copy master GCPs to target product.
     * @param group input master GCP group
     * @param targetGCPGroup output master GCP group
     */
    public static void copyGCPsToTarget(final ProductNodeGroup<Placemark> group, final ProductNodeGroup<Placemark> targetGCPGroup) {
        targetGCPGroup.removeAll();

        for(int i = 0; i < group.getNodeCount(); ++i) {
            final Placemark sPin = group.get(i);
            final Placemark tPin = new Placemark(sPin.getName(),
                               sPin.getLabel(),
                               sPin.getDescription(),
                               sPin.getPixelPos(),
                               sPin.getGeoPos(),
                               sPin.getSymbol());

            targetGCPGroup.add(tPin);
        }
    }

    public static Product createDummyTargetProduct(final Product[] sourceProducts) {
        final Product targetProduct = new Product(sourceProducts[0].getName(),
                                        sourceProducts[0].getProductType(),
                                        sourceProducts[0].getSceneRasterWidth(),
                                        sourceProducts[0].getSceneRasterHeight());

        OperatorUtils.copyProductNodes(sourceProducts[0], targetProduct);
        for(Product prod : sourceProducts) {
            for(Band band : prod.getBands()) {
                ProductUtils.copyBand(band.getName(), prod, band.getName(), targetProduct);
            }
        }
        return targetProduct;
    }

    public static String getAcquisitionDate(MetadataElement root) {
        String dateString;
        try {
            final ProductData.UTC date = root.getAttributeUTC(AbstractMetadata.first_line_time);
            final DateFormat dateFormat = ProductData.UTC.createDateFormat("dd.MMM.yyyy");
            dateString = dateFormat.format(date.getAsDate());
        } catch(Exception e) {
            dateString = "";
        }
        return dateString;
    }

    public static void createNewTiePointGridsAndGeoCoding(
            Product sourceProduct,
            Product targetProduct,
            int gridWidth,
            int gridHeight,
            float subSamplingX,
            float subSamplingY,
            PixelPos[] newTiePointPos) {

        TiePointGrid latGrid = null;
        TiePointGrid lonGrid = null;

        for(TiePointGrid srcTPG : sourceProduct.getTiePointGrids()) {

            final float[] tiePoints = new float[gridWidth*gridHeight];
            for (int k = 0; k < newTiePointPos.length; k++) {
                tiePoints[k] = srcTPG.getPixelFloat(newTiePointPos[k].x, newTiePointPos[k].y);
            }

            int discontinuity = TiePointGrid.DISCONT_NONE;
            if (srcTPG.getName().equals(TPG_LONGITUDE)) {
                discontinuity = TiePointGrid.DISCONT_AT_180;
            }

            final TiePointGrid tgtTPG = new TiePointGrid(srcTPG.getName(),
                                                   gridWidth,
                                                   gridHeight,
                                                   0.0f,
                                                   0.0f,
                                                   subSamplingX,
                                                   subSamplingY,
                                                   tiePoints,
                                                   discontinuity);

            targetProduct.addTiePointGrid(tgtTPG);

            if (srcTPG.getName().equals(TPG_LATITUDE)) {
                latGrid = tgtTPG;
            } else if (srcTPG.getName().equals(TPG_LONGITUDE)) {
                lonGrid = tgtTPG;
            }
        }

        final TiePointGeoCoding gc = new TiePointGeoCoding(latGrid, lonGrid);

        targetProduct.setGeoCoding(gc);
    }

    /** get the selected bands
     * @param sourceProduct the input product
     * @param sourceBandNames the select band names
     * @return band list
     */
    public static Band[] getSourceBands(final Product sourceProduct, String[] sourceBandNames) {

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
        return sourceBands;
    }

    public static void catchOperatorException(String opName, Exception e) throws OperatorException {
        if(opName.contains("$"))
            opName = opName.substring(0, opName.indexOf('$'));
        String message = opName + ":";
        if(e.getMessage() != null)
            message += e.getMessage();
        else
            message += e.toString();

        System.out.println(message);
        throw new OperatorException(message);
    }


    // mosaic and createStack scene functions

    /**
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     * @param sourceProducts the list of input products
     * @param scnProp the output scene properties
     */
    public static void computeImageGeoBoundary(final Product[] sourceProducts, final SceneProperties scnProp) {

        scnProp.latMin = 90.0;
        scnProp.latMax = -90.0;
        scnProp.lonMin = 180.0;
        scnProp.lonMax = -180.0;

        for (final Product srcProd : sourceProducts) {
            final GeoCoding geoCoding = srcProd.getGeoCoding();
            final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0, 0), null);
            final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(srcProd.getSceneRasterWidth() - 1, 0), null);
            final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(0, srcProd.getSceneRasterHeight() - 1), null);
            final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(srcProd.getSceneRasterWidth() - 1,
                    srcProd.getSceneRasterHeight() - 1), null);

            final double[] lats = {geoPosFirstNear.getLat(), geoPosFirstFar.getLat(), geoPosLastNear.getLat(), geoPosLastFar.getLat()};
            final double[] lons = {geoPosFirstNear.getLon(), geoPosFirstFar.getLon(), geoPosLastNear.getLon(), geoPosLastFar.getLon()};
            scnProp.srcCornerLatitudeMap.put(srcProd, lats);
            scnProp.srcCornerLongitudeMap.put(srcProd, lons);

            for (double lat : lats) {
                if (lat < scnProp.latMin) {
                    scnProp.latMin = lat;
                }
                if (lat > scnProp.latMax) {
                    scnProp.latMax = lat;
                }
            }

            for (double lon : lons) {
                if (lon < scnProp.lonMin) {
                    scnProp.lonMin = lon;
                }
                if (lon > scnProp.lonMax) {
                    scnProp.lonMax = lon;
                }
            }
        }
    }

    public static void getSceneDimensions(final double minSpacing, final SceneProperties scnProp) {
        double minAbsLat;
        if (scnProp.latMin * scnProp.latMax > 0) {
            minAbsLat = Math.min(Math.abs(scnProp.latMin), Math.abs(scnProp.latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
        } else {
            minAbsLat = 0.0;
        }
        double delLat = minSpacing / Constants.MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        double delLon = minSpacing / (Constants.MeanEarthRadius * Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
        delLat = Math.min(delLat, delLon);
        delLon = delLat;

        scnProp.sceneWidth = (int) ((scnProp.lonMax - scnProp.lonMin) / delLon) + 1;
        scnProp.sceneHeight = (int) ((scnProp.latMax - scnProp.latMin) / delLat) + 1;
    }

    /**
     * Add geocoding to the target product.
     * @param targetProduct the destination product
     * @param scnProp the scene properties
     */
    public static void addGeoCoding(final Product targetProduct, final OperatorUtils.SceneProperties scnProp) {

        final float[] latTiePoints = {(float) scnProp.latMax, (float) scnProp.latMax,
                (float) scnProp.latMin, (float) scnProp.latMin};
        final float[] lonTiePoints = {(float) scnProp.lonMin, (float) scnProp.lonMax,
                (float) scnProp.lonMin, (float) scnProp.lonMax};

        final int gridWidth = 10;
        final int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth * gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latTiePoints, fineLatTiePoints);

        float subSamplingX = (float) targetProduct.getSceneRasterWidth() / (gridWidth - 1);
        float subSamplingY = (float) targetProduct.getSceneRasterHeight() / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid(TPG_LATITUDE, gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth * gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonTiePoints, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid(TPG_LONGITUDE, gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);
    }

    public static class SceneProperties {
        public int sceneWidth, sceneHeight;
        public double latMin, lonMin, latMax, lonMax;

        public final Map<Product, double[]> srcCornerLatitudeMap = new HashMap<Product, double[]>(10);
        public final Map<Product, double[]> srcCornerLongitudeMap = new HashMap<Product, double[]>(10);
    }
}