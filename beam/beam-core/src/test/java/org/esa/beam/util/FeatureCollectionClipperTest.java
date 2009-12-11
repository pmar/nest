package org.esa.beam.util;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import junit.framework.TestCase;

import org.esa.beam.util.FeatureCollectionClipper;
import org.geotools.data.AbstractFeatureSource;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureListener;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.AttributeTypeImpl;
import org.geotools.feature.type.GeometryDescriptorImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;


public class FeatureCollectionClipperTest extends TestCase {

    public void testIt() throws IOException {

        GeometryType gt1 = new GeometryTypeImpl(new NameImpl("geometry"), Polygon.class,
                                                DefaultGeographicCRS.WGS84,
                                                false, false, null, null, null);

        AttributeType at2 = new AttributeTypeImpl(new NameImpl("label"), String.class,
                                                  false, false, null, null, null);

        GeometryDescriptor gd1 = new GeometryDescriptorImpl(gt1,
                                                            new NameImpl("GEOMETRY"),
                                                            0, 1,
                                                            false,
                                                            null);

        AttributeDescriptor ad2 = new AttributeDescriptorImpl(at2,
                                                              new NameImpl("LABEL"),
                                                              0, 1,
                                                              false,
                                                              null);


        SimpleFeatureType marcoType = new SimpleFeatureTypeImpl(new NameImpl("MarcoType"),
                                                                Arrays.asList(gd1, ad2),
                                                                gd1,
                                                                false, null, null, null);


        GeometryFactory gf = new GeometryFactory();
        Object[] data1 = {gf.toGeometry(new Envelope(0, 10, 0, 10)), "R1"};
        Object[] data2 = {gf.toGeometry(new Envelope(20, 30, 0, 10)), "R2"};
        Object[] data3 = {gf.toGeometry(new Envelope(40, 50, 0, 10)), "R3"};
        SimpleFeatureImpl f1 = new SimpleFeatureImpl(data1, marcoType, new FeatureIdImpl("F1"), true);
        SimpleFeatureImpl f2 = new SimpleFeatureImpl(data2, marcoType, new FeatureIdImpl("F2"), true);
        SimpleFeatureImpl f3 = new SimpleFeatureImpl(data3, marcoType, new FeatureIdImpl("F3"), true);

        MemoryDataStore dataStore = new MemoryDataStore(new SimpleFeature[]{f1, f2, f3});

        // F1 - no intersection
        // F2 - vertically clipped in the half
        // F3 - fully inside
        Geometry clipGeometry = gf.toGeometry(new Envelope(25, 55, -5, 15));

        FeatureSource<SimpleFeatureType, SimpleFeature> marcoSource = dataStore.getFeatureSource("MarcoType");
        assertNotNull(marcoSource);
        assertSame(dataStore, marcoSource.getDataStore());
        assertSame(marcoType, marcoSource.getSchema());
        assertEquals(3, marcoSource.getCount(Query.ALL));
        assertEquals("MarcoType", marcoSource.getName().toString());
        assertEquals(new ReferencedEnvelope(0, 50, 0, 10, DefaultGeographicCRS.WGS84),
                     marcoSource.getBounds());
        assertEquals(new ReferencedEnvelope(0, 50, 0, 10, DefaultGeographicCRS.WGS84),
                     marcoSource.getBounds(Query.ALL));

        FeatureCollection<SimpleFeatureType, SimpleFeature> normanSource = FeatureCollectionClipper.doOperation(
                marcoSource.getFeatures(), clipGeometry, null, null);

        assertNotNull(normanSource);
        assertEquals(marcoSource.getFeatures().getID(), normanSource.getID());
        assertEquals(marcoType, normanSource.getSchema());
        assertEquals(2, normanSource.size());
        assertEquals(25, normanSource.getBounds().getMinX(), 1e-10);
        assertEquals(50, normanSource.getBounds().getMaxX(), 1e-10);
        assertEquals(0, normanSource.getBounds().getMinY(), 1e-10);
        assertEquals(10, normanSource.getBounds().getMaxY(), 1e-10);
    }

    public static class MemoryFeatureSource extends AbstractFeatureSource {

        final MemoryDataStore dataStore;
        final SimpleFeatureType schema;

        public MemoryFeatureSource(SimpleFeatureType schema, SimpleFeature[] features, Set hints) {
            super(hints);
            this.schema = schema;
            this.dataStore = new MemoryDataStore();
        }


        @Override
        public DataStore getDataStore() {
            return dataStore;
        }

        public void addFeatureListener(FeatureListener listener) {
        }

        public void removeFeatureListener(FeatureListener listener) {
        }

        public SimpleFeatureType getSchema() {
            return schema;
        }
    }
}