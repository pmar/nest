package org.esa.beam.framework.ui.product;

import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glayer.CollectionLayer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerContext;
import com.bc.ceres.glayer.LayerFilter;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.support.ImageLayer;
import com.bc.ceres.glayer.support.LayerUtils;
import org.esa.beam.framework.datamodel.BitmaskDef;
import org.esa.beam.framework.datamodel.BitmaskOverlayInfo;
import org.esa.beam.framework.datamodel.GcpDescriptor;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.datamodel.PinDescriptor;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.draw.Figure;
import org.esa.beam.glayer.BitmaskCollectionLayer;
import org.esa.beam.glayer.BitmaskLayerType;
import org.esa.beam.glayer.FigureLayer;
import org.esa.beam.glayer.FigureLayerType;
import org.esa.beam.glayer.GraticuleLayer;
import org.esa.beam.glayer.GraticuleLayerType;
import org.esa.beam.glayer.NoDataLayerType;
import org.esa.beam.glayer.PlacemarkLayer;
import org.esa.beam.glayer.RasterImageLayerType;
import org.esa.beam.glayer.RgbImageLayerType;
import org.esa.beam.glayer.RoiLayerType;
import org.esa.beam.glevel.BandImageMultiLevelSource;
import org.esa.beam.util.PropertyMap;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

// todo - Layer API: make it implement ProductSceneViewContext
public class ProductSceneImage implements LayerContext {

    private static final ImageLayerFilter IMAGE_LAYER_FILTER = new ImageLayerFilter();
    private final String name;
    private final PropertyMap configuration;
    private RasterDataNode[] rasters;
    private Layer rootLayer;
    private BandImageMultiLevelSource bandImageMultiLevelSource;

    /**
     * Creates a color indexed product scene for the given product raster.
     *
     * @param raster        the product raster, must not be null
     * @param configuration a configuration
     * @param pm            a monitor to inform the user about progress @return a color indexed product scene image
     */
    public ProductSceneImage(RasterDataNode raster, PropertyMap configuration, ProgressMonitor pm) {
        this(raster.getDisplayName(),
             new RasterDataNode[]{raster},
             configuration);
        bandImageMultiLevelSource = BandImageMultiLevelSource.create(raster, pm);
        initRootLayer();
    }

    /**
     * Creates a new scene image for an existing view.
     *
     * @param raster The product raster.
     * @param view   An existing view.
     */
    public ProductSceneImage(RasterDataNode raster, ProductSceneView view) {
        this(raster.getDisplayName(),
             new RasterDataNode[]{raster},
             view.getSceneImage().getConfiguration());
        bandImageMultiLevelSource = view.getSceneImage().getBandImageMultiLevelSource();
        initRootLayer();
    }

    /**
     * Creates an RGB product scene for the given raster datasets.
     *
     * @param name          the name of the scene view
     * @param redRaster     the product raster used for the red color component, must not be null
     * @param greenRaster   the product raster used for the green color component, must not be null
     * @param blueRaster    the product raster used for the blue color component, must not be null
     * @param configuration a configuration
     * @param pm            a monitor to inform the user about progress @return an RGB product scene image @throws java.io.IOException if the image creation failed due to an I/O problem
     */
    public ProductSceneImage(String name, RasterDataNode redRaster,
                             RasterDataNode greenRaster,
                             RasterDataNode blueRaster,
                             PropertyMap configuration,
                             ProgressMonitor pm) {
        this(name, new RasterDataNode[]{redRaster, greenRaster, blueRaster}, configuration);
        bandImageMultiLevelSource = BandImageMultiLevelSource.create(rasters, pm);
        initRootLayer();
    }

    private ProductSceneImage(String name, RasterDataNode[] rasters, PropertyMap configuration) {
        this.name = name;
        this.rasters = rasters;
        this.configuration = configuration;
    }

    public PropertyMap getConfiguration() {
        return configuration;
    }

    public String getName() {
        return name;
    }

    public ImageInfo getImageInfo() {
        return bandImageMultiLevelSource.getImageInfo();
    }

    public void setImageInfo(ImageInfo imageInfo) {
        bandImageMultiLevelSource.setImageInfo(imageInfo);
    }

    public RasterDataNode[] getRasters() {
        return rasters;
    }

    public void setRasters(RasterDataNode[] rasters) {
        this.rasters = rasters;
    }

    @Override
    public Object getCoordinateReferenceSystem() {
        final GeoCoding geoCoding = rasters[0].getGeoCoding();
        if (geoCoding != null) {
            return geoCoding.getModelCRS();
        }
        return null;
    }

    @Override
    public Layer getRootLayer() {
        return rootLayer;
    }

    Layer getLayer(String id) {
        return LayerUtils.getChildLayerById(getRootLayer(), id);
    }

    void addLayer(int index, Layer layer) {
        rootLayer.getChildren().add(index, layer);
    }

    int getFirstImageLayerIndex() {
        return LayerUtils.getChildLayerIndex(getRootLayer(), IMAGE_LAYER_FILTER, LayerUtils.SearchMode.DEEP, 0);
    }

    ImageLayer getBaseImageLayer() {
        return (ImageLayer) getLayer(ProductSceneView.BASE_IMAGE_LAYER_ID);
    }

    Layer getNoDataLayer(boolean create) {
        Layer layer = getLayer(ProductSceneView.NO_DATA_LAYER_ID);
        if (layer == null && create) {
            layer = createNoDataLayer(getImageToModelTransform());
            addLayer(getFirstImageLayerIndex(), layer);
        }
        return layer;
    }

    Layer getBitmaskLayer(boolean create) {
        Layer layer = getLayer(ProductSceneView.BITMASK_LAYER_ID);
        if (layer == null && create) {
            layer = createBitmaskCollectionLayer(getImageToModelTransform());
            addLayer(getFirstImageLayerIndex(), layer);
        }
        return layer;
    }

    ImageLayer getRoiLayer(boolean create) {
        ImageLayer layer = (ImageLayer) getLayer(ProductSceneView.ROI_LAYER_ID);
        if (layer == null && create) {
            layer = createRoiLayer(getImageToModelTransform());
            addLayer(getFirstImageLayerIndex(), layer);
        }
        return layer;
    }

    GraticuleLayer getGraticuleLayer(boolean create) {
        GraticuleLayer layer = (GraticuleLayer) getLayer(ProductSceneView.GRATICULE_LAYER_ID);
        if (layer == null && create) {
            layer = createGraticuleLayer(getImageToModelTransform());
            addLayer(getFirstImageLayerIndex(), layer);
        }
        return layer;
    }

    Layer getGcpLayer(boolean create) {
        Layer layer = getLayer(ProductSceneView.GCP_LAYER_ID);
        if (layer == null && create) {
            layer = createGcpLayer(getImageToModelTransform());
            addLayer(0, layer);
        }
        return layer;
    }

    Layer getPinLayer(boolean create) {
        Layer layer = getLayer(ProductSceneView.PIN_LAYER_ID);
        if (layer == null && create) {
            layer = createPinLayer(getImageToModelTransform());
            addLayer(0, layer);
        }
        return layer;
    }

    FigureLayer getFigureLayer(boolean create) {
        FigureLayer layer = (FigureLayer) getLayer(ProductSceneView.FIGURE_LAYER_ID);
        if (layer == null && create) {
            layer = createFigureLayer(getImageToModelTransform());
            addLayer(getFirstImageLayerIndex(), layer);
        }
        return layer;
    }

    private RasterDataNode getRaster() {
        return rasters[0];
    }

    private void initRootLayer() {
        rootLayer = new CollectionLayer();
        addLayer(0, createBaseImageLayer());
    }

    public void initBitmaskLayer() {
        if (mustEnableBitmaskLayer()) {
            getBitmaskLayer(true);
        }
    }

    private boolean mustEnableBitmaskLayer() {
        final BitmaskOverlayInfo overlayInfo = getRaster().getBitmaskOverlayInfo();
        if (overlayInfo != null) {
            BitmaskDef[] defs = getRaster().getBitmaskDefs();
            for (BitmaskDef def : defs) {
                if (overlayInfo.containsBitmaskDef(def)) {
                    return true;
                }
            }
        }
        return false;
    }

    private AffineTransform getImageToModelTransform() {
        return bandImageMultiLevelSource.getModel().getImageToModelTransform(0);
    }

    private Layer createBaseImageLayer() {
        final Layer layer;
        if (getRasters().length == 1) {
            final RasterImageLayerType type = (RasterImageLayerType) LayerType.getLayerType(
                    RasterImageLayerType.class.getName());
            layer = type.createLayer(getRaster(), bandImageMultiLevelSource);
        } else {
            final RgbImageLayerType type = (RgbImageLayerType) LayerType.getLayerType(
                    RgbImageLayerType.class.getName());
            layer = type.createLayer(getRasters(), bandImageMultiLevelSource);
        }

        layer.setName(getName());
        layer.setVisible(true);
        layer.setId(ProductSceneView.BASE_IMAGE_LAYER_ID);
        setBaseImageLayerStyle(configuration, layer);
        return layer;
    }

    static void setBaseImageLayerStyle(PropertyMap configuration, Layer layer) {
        final boolean borderShown = configuration.getPropertyBool("image.border.shown",
                                                                  ImageLayer.DEFAULT_BORDER_SHOWN);
        final double borderWidth = configuration.getPropertyDouble("image.border.size",
                                                                   ImageLayer.DEFAULT_BORDER_WIDTH);
        final Color borderColor = configuration.getPropertyColor("image.border.color",
                                                                 ImageLayer.DEFAULT_BORDER_COLOR);

        final ValueContainer layerConfiguration = layer.getConfiguration();
        try {
            layerConfiguration.setValue(ImageLayer.PROPERTY_NAME_BORDER_SHOWN, borderShown);
            layerConfiguration.setValue(ImageLayer.PROPERTY_NAME_BORDER_WIDTH, borderWidth);
            layerConfiguration.setValue(ImageLayer.PROPERTY_NAME_BORDER_COLOR, borderColor);
        } catch (ValidationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Layer createNoDataLayer(AffineTransform imageToModelTransform) {
        final LayerType noDatatype = LayerType.getLayerType(NoDataLayerType.class.getName());
        final ValueContainer configTemplate = noDatatype.getConfigurationTemplate();

        final Color color = configuration.getPropertyColor("noDataOverlay.color", Color.ORANGE);
        try {
            configTemplate.setValue(NoDataLayerType.PROPERTY_NAME_COLOR, color);
            configTemplate.setValue(NoDataLayerType.PROPERTY_NAME_RASTER, getRaster());
            configTemplate.setValue(ImageLayer.PROPERTY_NAME_IMAGE_TO_MODEL_TRANSFORM, imageToModelTransform);
        } catch (ValidationException e) {
            throw new IllegalArgumentException(e);
        }
        return noDatatype.createLayer(this, configTemplate);
    }

    private Layer createBitmaskCollectionLayer(AffineTransform i2mTransform) {
        final LayerType bitmaskCollectionType = LayerType.getLayerType(BitmaskCollectionLayer.Type.class.getName());
        final ValueContainer layerConfig = bitmaskCollectionType.getConfigurationTemplate();
        try {
            layerConfig.setValue(BitmaskCollectionLayer.Type.PROPERTY_NAME_RASTER, getRaster());
            layerConfig.setValue(BitmaskCollectionLayer.Type.PROPERTY_NAME_IMAGE_TO_MODEL_TRANSFORM, i2mTransform);
        } catch (ValidationException e) {
            throw new IllegalArgumentException(e);
        }
        final Layer bitmaskCollectionLayer = bitmaskCollectionType.createLayer(this, layerConfig);
        final BitmaskDef[] bitmaskDefs = getRaster().getProduct().getBitmaskDefs();
        for (final BitmaskDef bitmaskDef : bitmaskDefs) {
            Layer layer = BitmaskLayerType.createBitmaskLayer(getRaster(), bitmaskDef, i2mTransform);
            bitmaskCollectionLayer.getChildren().add(layer);
        }
        return bitmaskCollectionLayer;
    }

    private FigureLayer createFigureLayer(AffineTransform i2mTransform) {
        final LayerType figureType = LayerType.getLayerType(FigureLayerType.class.getName());
        final ValueContainer template = figureType.getConfigurationTemplate();
        try {
            template.setValue(FigureLayer.PROPERTY_NAME_FIGURE_LIST, new ArrayList<Figure>());
            template.setValue(FigureLayer.PROPERTY_NAME_TRANSFORM, i2mTransform);
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTLINED,
                              configuration.getPropertyBool(FigureLayer.PROPERTY_NAME_SHAPE_OUTLINED,
                                                            FigureLayer.DEFAULT_SHAPE_OUTLINED));
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_COLOR,
                              configuration.getPropertyColor(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_COLOR,
                                                             FigureLayer.DEFAULT_SHAPE_OUTL_COLOR));
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_TRANSPARENCY,
                              configuration.getPropertyDouble(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_TRANSPARENCY,
                                                              FigureLayer.DEFAULT_SHAPE_OUTL_TRANSPARENCY));
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_WIDTH,
                              configuration.getPropertyDouble(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_WIDTH,
                                                              FigureLayer.DEFAULT_SHAPE_OUTL_WIDTH));
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_FILLED,
                              configuration.getPropertyBool(FigureLayer.PROPERTY_NAME_SHAPE_FILLED,
                                                            FigureLayer.DEFAULT_SHAPE_FILLED));
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_FILL_COLOR,
                              configuration.getPropertyColor(FigureLayer.PROPERTY_NAME_SHAPE_FILL_COLOR,
                                                             FigureLayer.DEFAULT_SHAPE_FILL_COLOR));
            template.setValue(FigureLayer.PROPERTY_NAME_SHAPE_FILL_TRANSPARENCY,
                              configuration.getPropertyDouble(FigureLayer.PROPERTY_NAME_SHAPE_FILL_TRANSPARENCY,
                                                              FigureLayer.DEFAULT_SHAPE_FILL_TRANSPARENCY));
        } catch (ValidationException e) {
            throw new IllegalStateException(e);
        }

        return (FigureLayer) figureType.createLayer(this, template);
    }

    static void setFigureLayerStyle(PropertyMap configuration, Layer layer) {
        final ValueContainer layerConfiguration = layer.getConfiguration();
        try {
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTLINED,
                                        configuration.getPropertyBool(FigureLayer.PROPERTY_NAME_SHAPE_OUTLINED,
                                                                      FigureLayer.DEFAULT_SHAPE_OUTLINED));
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_COLOR,
                                        configuration.getPropertyColor(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_COLOR,
                                                                       FigureLayer.DEFAULT_SHAPE_OUTL_COLOR));
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_TRANSPARENCY,
                                        configuration.getPropertyDouble(
                                                FigureLayer.PROPERTY_NAME_SHAPE_OUTL_TRANSPARENCY,
                                                FigureLayer.DEFAULT_SHAPE_OUTL_TRANSPARENCY));
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_WIDTH,
                                        configuration.getPropertyDouble(FigureLayer.PROPERTY_NAME_SHAPE_OUTL_WIDTH,
                                                                        FigureLayer.DEFAULT_SHAPE_OUTL_WIDTH));
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_FILLED,
                                        configuration.getPropertyBool(FigureLayer.PROPERTY_NAME_SHAPE_FILLED,
                                                                      FigureLayer.DEFAULT_SHAPE_FILLED));
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_FILL_COLOR,
                                        configuration.getPropertyColor(FigureLayer.PROPERTY_NAME_SHAPE_FILL_COLOR,
                                                                       FigureLayer.DEFAULT_SHAPE_FILL_COLOR));
            layerConfiguration.setValue(FigureLayer.PROPERTY_NAME_SHAPE_FILL_TRANSPARENCY,
                                        configuration.getPropertyDouble(
                                                FigureLayer.PROPERTY_NAME_SHAPE_FILL_TRANSPARENCY,
                                                FigureLayer.DEFAULT_SHAPE_FILL_TRANSPARENCY));
        } catch (ValidationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private ImageLayer createRoiLayer(AffineTransform imageToModelTransform) {
        final LayerType roiLayerType = LayerType.getLayerType(RoiLayerType.class.getName());

        final Color color = configuration.getPropertyColor(RoiLayerType.PROPERTY_NAME_COLOR, Color.RED);
        final double transparency = configuration.getPropertyDouble(RoiLayerType.PROPERTY_NAME_TRANSPARENCY, 0.5);

        final ValueContainer template = roiLayerType.getConfigurationTemplate();
        try {
            template.setValue(RoiLayerType.PROPERTY_NAME_COLOR, color);
            template.setValue(RoiLayerType.PROPERTY_NAME_TRANSPARENCY, transparency);
            template.setValue(RoiLayerType.PROPERTY_NAME_RASTER, getRaster());
            template.setValue(ImageLayer.PROPERTY_NAME_IMAGE_TO_MODEL_TRANSFORM, imageToModelTransform);
        } catch (ValidationException e) {
            throw new IllegalArgumentException(e);
        }
        return (ImageLayer) roiLayerType.createLayer(this, template);
    }

    private GraticuleLayer createGraticuleLayer(AffineTransform i2mTransform) {
        final LayerType layerType = LayerType.getLayerType(GraticuleLayerType.class.getName());
        final ValueContainer template = layerType.getConfigurationTemplate();
        try {
            template.setValue(GraticuleLayerType.PROPERTY_NAME_RASTER, getRaster());
            template.setValue(GraticuleLayerType.PROPERTY_NAME_TRANSFORM, i2mTransform);
        } catch (ValidationException e) {
            throw new IllegalArgumentException(e);
        }
        final GraticuleLayer graticuleLayer = (GraticuleLayer) layerType.createLayer(null, template);
        graticuleLayer.setId(ProductSceneView.GRATICULE_LAYER_ID);
        graticuleLayer.setVisible(false);
        graticuleLayer.setName("Graticule");
        setGraticuleLayerStyle(configuration, graticuleLayer);
        return graticuleLayer;
    }

    static void setGraticuleLayerStyle(PropertyMap configuration, Layer layer) {
        final ValueContainer layerConfiguration = layer.getConfiguration();

        try {
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_RES_AUTO,
                                        configuration.getPropertyBool(GraticuleLayerType.PROPERTY_NAME_RES_AUTO,
                                                                      GraticuleLayerType.DEFAULT_RES_AUTO));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_RES_PIXELS,
                                        configuration.getPropertyInt(GraticuleLayerType.PROPERTY_NAME_RES_PIXELS,
                                                                     GraticuleLayerType.DEFAULT_RES_PIXELS));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_RES_LAT,
                                        configuration.getPropertyDouble(GraticuleLayerType.PROPERTY_NAME_RES_LAT,
                                                                        GraticuleLayerType.DEFAULT_RES_LAT));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_RES_LON,
                                        configuration.getPropertyDouble(GraticuleLayerType.PROPERTY_NAME_RES_LON,
                                                                        GraticuleLayerType.DEFAULT_RES_LON));

            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_LINE_COLOR,
                                        configuration.getPropertyColor(GraticuleLayerType.PROPERTY_NAME_LINE_COLOR,
                                                                       GraticuleLayerType.DEFAULT_LINE_COLOR));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_LINE_WIDTH,
                                        configuration.getPropertyDouble(GraticuleLayerType.PROPERTY_NAME_LINE_WIDTH,
                                                                        GraticuleLayerType.DEFAULT_LINE_WIDTH));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_LINE_TRANSPARENCY,
                                        configuration.getPropertyDouble(
                                                GraticuleLayerType.PROPERTY_NAME_LINE_TRANSPARENCY,
                                                GraticuleLayerType.DEFAULT_LINE_TRANSPARENCY));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_TEXT_ENABLED,
                                        configuration.getPropertyBool(GraticuleLayerType.PROPERTY_NAME_TEXT_ENABLED,
                                                                      GraticuleLayerType.DEFAULT_TEXT_ENABLED));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_TEXT_FG_COLOR,
                                        configuration.getPropertyColor(GraticuleLayerType.PROPERTY_NAME_TEXT_FG_COLOR,
                                                                       GraticuleLayerType.DEFAULT_TEXT_FG_COLOR));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_TEXT_BG_COLOR,
                                        configuration.getPropertyColor(GraticuleLayerType.PROPERTY_NAME_TEXT_BG_COLOR,
                                                                       GraticuleLayerType.DEFAULT_TEXT_BG_COLOR));
            layerConfiguration.setValue(GraticuleLayerType.PROPERTY_NAME_TEXT_BG_TRANSPARENCY,
                                        configuration.getPropertyDouble(
                                                GraticuleLayerType.PROPERTY_NAME_TEXT_BG_TRANSPARENCY,
                                                GraticuleLayerType.DEFAULT_TEXT_BG_TRANSPARENCY));
        } catch (ValidationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private PlacemarkLayer createPinLayer(AffineTransform i2mTransform) {
        final PlacemarkLayer pinLayer = new PlacemarkLayer(getRaster().getProduct(), PinDescriptor.INSTANCE,
                                                           i2mTransform);
        pinLayer.setName("Pins");
        pinLayer.setId(ProductSceneView.PIN_LAYER_ID);
        pinLayer.setVisible(false);
        setPinLayerStyle(configuration, pinLayer);

        return pinLayer;
    }

    static void setPinLayerStyle(PropertyMap configuration, Layer layer) {
        final ValueContainer layerConfiguration = layer.getConfiguration();

        try {
            layerConfiguration.setValue(PlacemarkLayer.PROPERTY_NAME_TEXT_ENABLED,
                                        configuration.getPropertyBool("pin.text.enabled", Boolean.TRUE));
            layerConfiguration.setValue(PlacemarkLayer.PROPERTY_NAME_TEXT_FG_COLOR,
                                        configuration.getPropertyColor("pin.text.fg.color", Color.WHITE));
            layerConfiguration.setValue(PlacemarkLayer.PROPERTY_NAME_TEXT_BG_COLOR,
                                        configuration.getPropertyColor("pin.text.bg.color", Color.BLACK));
        } catch (ValidationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private PlacemarkLayer createGcpLayer(AffineTransform i2mTransform) {
        final PlacemarkLayer gcpLayer = new PlacemarkLayer(getRaster().getProduct(), GcpDescriptor.INSTANCE,
                                                           i2mTransform);
        gcpLayer.setName("Ground Control Points");
        gcpLayer.setId(ProductSceneView.GCP_LAYER_ID);
        gcpLayer.setVisible(false);
        setGcpLayerStyle(configuration, gcpLayer);

        return gcpLayer;
    }

    static void setGcpLayerStyle(PropertyMap configuration, Layer layer) {
        final ValueContainer layerConfiguration = layer.getConfiguration();

        try {
            layerConfiguration.setValue(PlacemarkLayer.PROPERTY_NAME_TEXT_ENABLED,
                                        configuration.getPropertyBool("gcp.text.enabled", Boolean.TRUE));
            layerConfiguration.setValue(PlacemarkLayer.PROPERTY_NAME_TEXT_FG_COLOR,
                                        configuration.getPropertyColor("gcp.text.fg.color", Color.WHITE));
            layerConfiguration.setValue(PlacemarkLayer.PROPERTY_NAME_TEXT_BG_COLOR,
                                        configuration.getPropertyColor("gcp.text.bg.color", Color.BLACK));
        } catch (ValidationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private BandImageMultiLevelSource getBandImageMultiLevelSource() {
        return bandImageMultiLevelSource;
    }

    private static class ImageLayerFilter implements LayerFilter {

        @Override
        public boolean accept(Layer layer) {
            return layer instanceof ImageLayer;
        }
    }
}