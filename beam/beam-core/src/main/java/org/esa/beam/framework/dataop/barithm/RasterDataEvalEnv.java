/*
 * $Id: RasterDataEvalEnv.java,v 1.2 2009-05-22 15:31:30 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
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
package org.esa.beam.framework.dataop.barithm;

import com.bc.jexp.EvalEnv;

/**
 * Represents an evaluation environment for {@link com.bc.jexp.Term Terms} which are operating on raster data.
 * <p>The evaluation environment is passed to the {@link com.bc.jexp.Term#evalB(com.bc.jexp.EvalEnv) evalB},
 * {@link com.bc.jexp.Term#evalI(com.bc.jexp.EvalEnv) evalI} and
 * {@link com.bc.jexp.Term#evalB(com.bc.jexp.EvalEnv) evalB} methods
 * of a {@link com.bc.jexp.Term Term}.
 * <p>Special implementations of the {@link com.bc.jexp.Symbol Symbol} and {@link com.bc.jexp.Function Function}
 * interfaces, such as {@link RasterDataSymbol}, can then use the environment in order to perform
 * raster data specific evaluations.
 */
public class RasterDataEvalEnv implements EvalEnv {

    private final int _offsetX;
    private final int _offsetY;
    private final int _regionWidth;
    private final int _regionHeight;
    private int _pixelX;
    private int _pixelY;
    private int _elemIndex;

    /**
     * Constructs a new environment for the given raster data region.
     *
     * @param offsetX      the x-offset of the raster region
     * @param offsetY      the y-offset of the raster region
     * @param regionWidth  the width of the raster region
     * @param regionHeight the height of the raster region
     */
    public RasterDataEvalEnv(int offsetX, int offsetY, int regionWidth, int regionHeight) {
        _offsetX = offsetX;
        _offsetY = offsetY;
        _regionWidth = regionWidth;
        _regionHeight = regionHeight;
    }

    /**
     * Gets the x-offset of the raster region.
     *
     * @return the x-offset of the raster region
     */
    public int getOffsetX() {
        return _offsetX;
    }

    /**
     * Gets the y-offset of the raster region.
     *
     * @return the y-offset of the raster region.
     */
    public int getOffsetY() {
        return _offsetY;
    }

    /**
     * Gets the width of the raster region.
     *
     * @return the width of the raster region.
     */
    public int getRegionWidth() {
        return _regionWidth;
    }

    /**
     * Gets the height of the raster region.
     *
     * @return the height of the raster region.
     */
    public int getRegionHeight() {
        return _regionHeight;
    }

    /**
     * Gets the absolute pixel's x-coordinate within the data raster.
     *
     * @return the current pixel's x-coordinate
     */
    public final int getPixelX() {
        return _pixelX;
    }

    /**
     * Sets the absolute pixel's x-coordinate within the data raster.
     *
     * @param pixelX the current pixel's x-coordinate
     */
    public void setPixelX(int pixelX) {
        _pixelX = pixelX;
    }

    /**
     * Gets the absolute pixel's y-coordinate within the data raster.
     *
     * @return the current pixel's y-coordinate
     */
    public final int getPixelY() {
        return _pixelY;
    }

    /**
     * Sets the absolute pixel's y-coordinate within the data raster.
     *
     * @param pixelY the current pixel's y-coordinate
     */
    public void setPixelY(int pixelY) {
        _pixelY = pixelY;
    }

    /**
     * Gets the index of the current data element.
     *
     * @return the index of the current data element
     */
    public final int getElemIndex() {
        return _elemIndex;
    }

    /**
     * Sets the index of the current data element.
     *
     * @param elemIndex the index of the current data element
     */
    public final void setElemIndex(int elemIndex) {
        _elemIndex = elemIndex;
    }
}