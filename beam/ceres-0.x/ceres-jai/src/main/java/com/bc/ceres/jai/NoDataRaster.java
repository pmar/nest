package com.bc.ceres.jai;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

/**
 * A {@link Raster} representing a tile with no interpretable data. Note
 * that the sole purpose of this class is to mark tiles, which are known
 * to contain no interpretable data.
 *
 * @author Ralf Quast
 * @version $Revision: 1.1 $ $Date: 2009-11-04 17:04:33 $
 * @since Ceres 0.10
 */
public final class NoDataRaster extends Raster {

    private final Raster delegate;

    /**
     * Constructs a NoDataRaster with the SampleModel, DataBuffer and origin
     * of the given Raster
     *
     * @param raster The Raster representing a tile with no interpretable
     *               data.
     */
    public NoDataRaster(Raster raster) {
        super(raster.getSampleModel(), raster.getDataBuffer(),
              new Point(raster.getSampleModelTranslateX(), raster.getSampleModelTranslateY()));
        this.delegate = raster;
    }

    @Override
    public final Raster getParent() {
        return delegate.getParent();
    }

    @Override
    public final WritableRaster createCompatibleWritableRaster() {
        return delegate.createCompatibleWritableRaster();
    }

    @Override
    public final WritableRaster createCompatibleWritableRaster(int w, int h) {
        return delegate.createCompatibleWritableRaster(w, h);
    }

    @Override
    public final WritableRaster createCompatibleWritableRaster(Rectangle rect) {
        return delegate.createCompatibleWritableRaster(rect);
    }

    @Override
    public final WritableRaster createCompatibleWritableRaster(int x, int y, int w, int h) {
        return delegate.createCompatibleWritableRaster(x, y, w, h);
    }

    @Override
    public final Raster createChild(int parentX, int parentY, int width, int height, int childMinX, int childMinY,
                                    int[] bandList) {
        return delegate.createChild(parentX, parentY, width, height, childMinX, childMinY, bandList);
    }

    @Override
    public final NoDataRaster createTranslatedChild(int childMinX, int childMinY) {
        final Raster child = delegate.createTranslatedChild(childMinX, childMinY);
        if (child instanceof NoDataRaster) {
            return (NoDataRaster) child;
        }
        return new NoDataRaster(child);
    }

    @Override
    public final Rectangle getBounds() {
        return delegate.getBounds();
    }

    @Override
    public final DataBuffer getDataBuffer() {
        return delegate.getDataBuffer();
    }

    @Override
    public final SampleModel getSampleModel() {
        return delegate.getSampleModel();
    }

    @Override
    public final Object getDataElements(int x, int y, Object outData) {
        return delegate.getDataElements(x, y, outData);
    }

    @Override
    public final Object getDataElements(int x, int y, int w, int h, Object outData) {
        return delegate.getDataElements(x, y, w, h, outData);
    }

    @Override
    public final int[] getPixel(int x, int y, int[] iArray) {
        return delegate.getPixel(x, y, iArray);
    }

    @Override
    public final float[] getPixel(int x, int y, float[] fArray) {
        return delegate.getPixel(x, y, fArray);
    }

    @Override
    public final double[] getPixel(int x, int y, double[] dArray) {
        return delegate.getPixel(x, y, dArray);
    }

    @Override
    public final int[] getPixels(int x, int y, int w, int h, int[] iArray) {
        return delegate.getPixels(x, y, w, h, iArray);
    }

    @Override
    public final float[] getPixels(int x, int y, int w, int h, float[] fArray) {
        return delegate.getPixels(x, y, w, h, fArray);
    }

    @Override
    public final double[] getPixels(int x, int y, int w, int h, double[] dArray) {
        return delegate.getPixels(x, y, w, h, dArray);
    }

    @Override
    public final int getSample(int x, int y, int b) {
        return delegate.getSample(x, y, b);
    }

    @Override
    public final float getSampleFloat(int x, int y, int b) {
        return delegate.getSampleFloat(x, y, b);
    }

    @Override
    public final double getSampleDouble(int x, int y, int b) {
        return delegate.getSampleDouble(x, y, b);
    }

    @Override
    public final int[] getSamples(int x, int y, int w, int h, int b, int[] iArray) {
        return delegate.getSamples(x, y, w, h, b, iArray);
    }

    @Override
    public final float[] getSamples(int x, int y, int w, int h, int b, float[] fArray) {
        return delegate.getSamples(x, y, w, h, b, fArray);
    }

    @Override
    public final double[] getSamples(int x, int y, int w, int h, int b, double[] dArray) {
        return delegate.getSamples(x, y, w, h, b, dArray);
    }
}