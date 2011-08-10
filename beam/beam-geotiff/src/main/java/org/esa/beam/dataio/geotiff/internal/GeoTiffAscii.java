/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.dataio.geotiff.internal;

/**
 * A TIFFValue implementation for the GeoTIFF format.
 *
 * @author Marco Peters
 * @author Sabine Embacher
 * @author Norman Fomferra
 * @version $Revision: 1.3 $ $Date: 2010-12-23 14:35:09 $
 */
class GeoTiffAscii extends TiffAscii {

    public GeoTiffAscii(final String ... values) {
        super(appendTerminator(values));
    }

    private static String appendTerminator(String... values) {
        final StringBuffer buffer = new StringBuffer();
        for (String value : values) {
            buffer.append(value).append("|");
        }
        return buffer.toString();
    }
}