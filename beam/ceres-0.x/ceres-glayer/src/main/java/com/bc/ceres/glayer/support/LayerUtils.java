package com.bc.ceres.glayer.support;

import com.bc.ceres.core.Assert;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerFilter;
import com.bc.ceres.glayer.support.filters.IdFilter;
import com.bc.ceres.glayer.support.filters.NameFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public class LayerUtils {
    public enum SearchMode {
        FLAT,
        DEEP,
    }

    public final static SearchMode SEARCH_FLAT = SearchMode.FLAT;
    public final static SearchMode SEARCH_DEEP = SearchMode.DEEP;

    private LayerUtils() {
    }

    public static int getChildLayerIndex(Layer root, SearchMode mode, int defaultIndex, LayerFilter filter) {
        Assert.notNull(root, "root");
        Assert.notNull(filter, "filter");
        Assert.notNull(mode, "mode");

        List<Layer> children = root.getChildren();
        for (int index = 0; index < children.size(); index++) {
            Layer child = children.get(index);
            if (filter.accept(child)) {
                return index;
            }
        }
        if (mode == SEARCH_DEEP) {
            for (int index = 0; index < children.size(); index++) {
                Layer child = children.get(index);
                if (getChildLayer(child, SEARCH_DEEP, filter) != null) {
                    return index;
                }
            }
        }
        return defaultIndex;
    }

    public static Layer getChildLayerById(Layer root, String id) {
        return getChildLayer(root, SEARCH_DEEP, new IdFilter(id));
    }

    public static Layer getChildLayerByName(Layer root, String name) {
        return getChildLayer(root, SEARCH_DEEP, new NameFilter(name));
    }

    public static Layer getChildLayer(Layer root, SearchMode mode, LayerFilter filter) {
        Assert.notNull(root, "root");
        Assert.notNull(filter, "filter");
        Assert.notNull(mode, "mode");

        for (Layer child : root.getChildren()) {
            if (filter.accept(child)) {
                return child;
            }
        }
        if (mode == SEARCH_DEEP) {
            for (Layer child : root.getChildren()) {
                Layer acceptedLayer = getChildLayer(child, SEARCH_DEEP, filter);
                if (acceptedLayer != null) {
                    return acceptedLayer;
                }
            }
        }
        return null;
    }

    public static List<Layer> getChildLayers(Layer root, SearchMode mode, LayerFilter filter) {
        Assert.notNull(root, "root");
        Assert.notNull(mode, "mode");
        Assert.notNull(filter, "filter");

        ArrayList<Layer> layers = new ArrayList<Layer>(16);
        getChildLayers(root, mode, filter, layers);
        return layers;
    }

    private static void getChildLayers(Layer root, SearchMode mode, LayerFilter filter, List<Layer> layers) {
        for (Layer child : root.getChildren()) {
            if (filter.accept(child)) {
                layers.add(child);
            }
        }

        if (mode == SEARCH_DEEP) {
            for (Layer child : root.getChildren()) {
                getChildLayers(child, SEARCH_DEEP, filter, layers);
            }
        }
    }

    public static Layer[] getLayerPath(Layer root, Layer layer) {
        Assert.notNull(root, "root");
        Assert.notNull(layer, "layer");

        if (root == layer) {
            return new Layer[]{root};
        }
        final ArrayList<Layer> layerArrayList = new ArrayList<Layer>();
        collectLayerPath(root, layer, layerArrayList);
        return layerArrayList.toArray(new Layer[layerArrayList.size()]);
    }

    public static Collection<Layer[]> getLayerPaths(Layer root, LayerFilter filter) {
        // todo - implement (nf)
        throw new IllegalStateException("not implemented");
    }

    private static boolean collectLayerPath(Layer root, Layer layer, List<Layer> collection) {
        List<Layer> children = root.getChildren();
        if (children.contains(layer)) {
            collection.add(root);
            collection.add(layer);
            return true;
        }
        for (Layer child : children) {
            if (collectLayerPath(child, layer, collection)) {
                collection.add(0, root);
                return true;
            }
        }
        return false;
    }


    /**
     * @deprecated since BEAM 4.7
     */
    @Deprecated
    public static int getChildLayerIndex(Layer root, LayerFilter filter, SearchMode mode, int defaultIndex) {
        return getChildLayerIndex(root, mode, defaultIndex, filter);
    }

    /**
     * @deprecated since BEAM 4.7
     */
    @Deprecated
    public static Layer getChildLayer(Layer root, LayerFilter filter, SearchMode mode) {
        return getChildLayer(root, mode, filter);
    }

}
