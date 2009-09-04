package org.esa.nest.dataio.ceos.radarsat;

import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.ImageRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


/**
 * This class represents an image file of a CEOS product.

 */
class RadarsatImageFile extends CEOSImageFile {

    private final static String mission = "radarsat";
    private final static String image_recordDefinitionFile = "image_file.xml";
    private final static String image_recordDefinition = "image_record.xml";

    public RadarsatImageFile(final ImageInputStream imageStream, final BaseRecord histogramRecord)
            throws IOException, IllegalBinaryFormatException {
        binaryReader = new BinaryFileReader(imageStream);
        _imageFDR = new BaseRecord(binaryReader, -1, mission, image_recordDefinitionFile);
        binaryReader.seek(_imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()));
        if(getRasterHeight() == 0) {
            final int height = histogramRecord.getAttributeInt("Data samples in line");
            _imageFDR.getCeosDatabase().set("Number of lines per data set", height);
        }
        _imageRecords = new ImageRecord[getRasterHeight()];
        _imageRecords[0] = new ImageRecord(binaryReader, -1, mission, image_recordDefinition);

        _imageRecordLength = _imageRecords[0].getRecordLength();
        _startPosImageRecords = _imageRecords[0].getStartPos();
    }

}