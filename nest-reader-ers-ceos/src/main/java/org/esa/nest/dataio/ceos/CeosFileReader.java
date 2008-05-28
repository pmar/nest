/*
 * $Id: CeosFileReader.java,v 1.8 2008-05-27 20:45:24 lveci Exp $
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
package org.esa.nest.dataio.ceos;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/**
 * A reader for reading files in the CEOS format
 *
 */
public class CeosFileReader {

    private static final String EM_EXPECTED_X_FOUND_Y_BYTES = "Expected bytes to read %d, but only found %d";
    private static final String EM_READING_X_TYPE = "Reading '%s'-Type";
    private static final String EM_NOT_PARSABLE_X_STRING = "Not able to parse %s string";

    private final ImageInputStream _stream;

    public CeosFileReader(final ImageInputStream stream) {
        _stream = stream;
    }

    public void close() throws IOException {
        _stream.close();
    }

    public void seek(final long pos) throws IOException {
        _stream.seek(pos);
    }

    public void skipBytes(final long numBytes) throws IOException {
        _stream.skipBytes(numBytes);
    }

    public int readB1() throws IOException,
                               IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readByte() & 0xFF;
        } catch (IOException e) {
            final String message = String.format(CeosFileReader.EM_READING_X_TYPE,
                                                 new Object[]{"B1"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
    }

    public short readB2() throws IOException,
                                 IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readShort();
        } catch (IOException e) {
            final String message = String.format(CeosFileReader.EM_READING_X_TYPE,
                                                 new Object[]{"B2"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
    }

    public int readB4() throws IOException,
                               IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readInt();
        } catch (IOException e) {
            final String message = String.format(CeosFileReader.EM_READING_X_TYPE,
                                                 new Object[]{"B4"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
    }

    public long readB8() throws IOException,
                                IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        try {
            return _stream.readLong();
        } catch (IOException e) {
            final String message = String.format(CeosFileReader.EM_READING_X_TYPE,
                                                 new Object[]{"B8"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
    }

    public void readB1(final byte[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void readB2(final short[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void readB4(final int[] array) throws IOException {
            _stream.readFully(array, 0, array.length);
    }

    public void readB8(final long[] array) throws IOException, IllegalCeosFormatException {
            _stream.readFully(array, 0, array.length);
    }

    public long readIn(final int n) throws IOException,
                                           IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final String longStr = readAn(n).trim();
        if(longStr.isEmpty()) return 0;
        return parseLong(longStr, streamPosition);
    }

    private static long parseLong(String integerStr, long streamPosition) throws IllegalCeosFormatException {
        final long number;
        try {
            number = Long .parseLong(integerStr);
        } catch (NumberFormatException e) {
            final String message = String.format(CeosFileReader.EM_NOT_PARSABLE_X_STRING + " \"" + integerStr + '"',
                                                                    new Object[]{"integer"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
        return number;
    }

    public double readFn(final int n) throws IOException,
                                             IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final String doubleString = readAn(n).trim();
        if(doubleString.isEmpty()) return 0;
        try {
            return Double.parseDouble(doubleString);
        } catch (NumberFormatException e) {
            final String message = String.format(CeosFileReader.EM_NOT_PARSABLE_X_STRING,
                                                 new Object[]{"double"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
    }

    public void readFn(final int n, final double[] numbers) throws IOException,
                                                                   IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        for (int i = 0; i < numbers.length; i++) {
            try {
                numbers[i] = Double.parseDouble(readAn(n).trim());
            } catch (IllegalCeosFormatException e) {
                final String message = String.format(CeosFileReader.EM_READING_X_TYPE,
                                                     new Object[]{"Gn[]"});
                throw new IllegalCeosFormatException(message, streamPosition, e);
            }
        }
    }

    public String readAn(final int n) throws IOException,
                                             IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final byte[] bytes = new byte[n];
        final int bytesRead;
        try {
            bytesRead = _stream.read(bytes);
        } catch (IOException e) {
            final String message = String.format(CeosFileReader.EM_READING_X_TYPE,
                                                 new Object[]{"An"});
            throw new IllegalCeosFormatException(message, streamPosition, e);
        }
        if (bytesRead != n) {
            final String message = String.format(CeosFileReader.EM_EXPECTED_X_FOUND_Y_BYTES,
                                                 new Object[]{n, bytesRead});
            throw new IllegalCeosFormatException(message, streamPosition);
        }
        return new String(bytes);
    }

    public int[] readInArray(final int arraySize, final int intValLength) throws
                                                                          IOException,
                                                                          IllegalCeosFormatException {
        final long streamPosition = _stream.getStreamPosition();
        final int[] ints = new int[arraySize];
        for (int i = 0; i < ints.length; i++) {
            final String integerString = readAn(intValLength).trim();
            if (integerString.length() > 0) {
                ints[i] = (int) parseLong(integerString, streamPosition + i * intValLength);
            }
        }
        return ints;
    }

    public long getCurrentPos() throws IOException {
        return _stream.getStreamPosition();
    }
}
