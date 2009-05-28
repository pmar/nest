package org.esa.beam.framework.gpf.graph;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.*;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

public class SourceProductAnnotationValidationTest extends TestCase {

    private OperatorSpi wrongTypeOpSpi;
    private OperatorSpi wrongBandsOpSpi;
    private OperatorSpi goodOpSpi;
    private OperatorSpi consumerOpSpi;
    private OperatorSpi optionalConsumerOpSpi;
    private OperatorSpi aliasConsumerOpSpi;

    @Override
    protected void setUp() throws Exception {
        wrongTypeOpSpi = new WrongTypeOperator.Spi();
        final OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        spiRegistry.addOperatorSpi(wrongTypeOpSpi);
        wrongBandsOpSpi = new WrongBandsOperator.Spi();
        spiRegistry.addOperatorSpi(wrongBandsOpSpi);
        goodOpSpi = new GoodOperator.Spi();
        spiRegistry.addOperatorSpi(goodOpSpi);
        consumerOpSpi = new ConsumerOperator.Spi();
        spiRegistry.addOperatorSpi(consumerOpSpi);
        aliasConsumerOpSpi = new ConsumerWithAliasSourceOperator.Spi();
        spiRegistry.addOperatorSpi(aliasConsumerOpSpi);
        optionalConsumerOpSpi = new OptionalConsumerOperator.Spi();
        spiRegistry.addOperatorSpi(optionalConsumerOpSpi);

    }

    @Override
    protected void tearDown() {
        final OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        spiRegistry.removeOperatorSpi(wrongTypeOpSpi);
        spiRegistry.removeOperatorSpi(wrongBandsOpSpi);
        spiRegistry.removeOperatorSpi(goodOpSpi);
        spiRegistry.removeOperatorSpi(consumerOpSpi);
        spiRegistry.removeOperatorSpi(aliasConsumerOpSpi);
        spiRegistry.removeOperatorSpi(optionalConsumerOpSpi);
    }

    public void testForWrongType() {
        Graph graph = new Graph("graph");

        Node wrongTypeNode = new Node("WrongType", wrongTypeOpSpi.getOperatorAlias());
        Node consumerNode = new Node("Consumer", consumerOpSpi.getOperatorAlias());
        consumerNode.addSource(new NodeSource("input1", "WrongType"));
        graph.addNode(wrongTypeNode);
        graph.addNode(consumerNode);

        try {
            new GraphProcessor().createGraphContext(graph, ProgressMonitor.NULL);
            fail("GraphException expected caused by wrong type of source product");
        } catch (GraphException ge) {
        }
    }

    public void testForWrongBands() {
        Graph graph = new Graph("graph");

        Node wrongBandsNode = new Node("WrongBands", wrongBandsOpSpi.getOperatorAlias());
        Node consumerNode = new Node("Consumer", consumerOpSpi.getOperatorAlias());
        consumerNode.addSource(new NodeSource("input1", "WrongBands"));
        graph.addNode(wrongBandsNode);
        graph.addNode(consumerNode);

        try {
            new GraphProcessor().createGraphContext(graph, ProgressMonitor.NULL);
            fail("GraphException expected, caused by missing bands");
        } catch (GraphException ge) {
        }
    }

    public void testOptionalAndWrongProductIsGiven() {
        Graph graph = new Graph("graph");

        Node wrongBandsNode = new Node("WrongBands", wrongBandsOpSpi.getOperatorAlias());
        Node consumerNode = new Node("OptionalConsumer", optionalConsumerOpSpi.getOperatorAlias());
        consumerNode.addSource(new NodeSource("input1", "WrongBands"));
        graph.addNode(wrongBandsNode);
        graph.addNode(consumerNode);

        try {
            new GraphProcessor().createGraphContext(graph, ProgressMonitor.NULL);
            fail("GraphException expected, caused by missing bands, even if optional");
        } catch (GraphException ge) {
        }
    }

    public void testOptionalAndWrongProductIsNotGiven() throws GraphException {
        Graph graph = new Graph("graph");

        Node wrongBandsNode = new Node("WrongBands", wrongBandsOpSpi.getOperatorAlias());
        Node consumerNode = new Node("OptionalConsumer", optionalConsumerOpSpi.getOperatorAlias());
        graph.addNode(wrongBandsNode);
        graph.addNode(consumerNode);

        new GraphProcessor().createGraphContext(graph, ProgressMonitor.NULL);
    }

    public void testNotInitialzedInputResultsInException() {
        Graph graph = new Graph("graph");

        Node goodNode = new Node("Good", goodOpSpi.getOperatorAlias());
        Node consumerNode = new Node("Consumer", consumerOpSpi.getOperatorAlias());
        graph.addNode(goodNode);
        graph.addNode(consumerNode);

        try {
            new GraphProcessor().createGraphContext(graph, ProgressMonitor.NULL);
            fail("GraphException expected, because input1 is not initialized");
        } catch (GraphException ge) {
        }
    }

    public void testSourceProductWithAlias() throws GraphException {
        Graph graph = new Graph("graph");

        Node goodNode = new Node("Good", goodOpSpi.getOperatorAlias());
        Node consumerNode = new Node("AliasConsumer", aliasConsumerOpSpi.getOperatorAlias());
        consumerNode.addSource(new NodeSource("alias", "Good"));
        graph.addNode(goodNode);
        graph.addNode(consumerNode);

        GraphContext graphContext = new GraphProcessor().createGraphContext(graph, ProgressMonitor.NULL);
        NodeContext consumerNodeContext = graphContext.getNodeContext(consumerNode);
        assertSame(((ConsumerWithAliasSourceOperator) consumerNodeContext.getOperator()).input1,
                   consumerNodeContext.getSourceProduct("alias"));
    }

    public static class WrongTypeOperator extends Operator {

        @TargetProduct
        Product output;

        @Override
        public void initialize() throws OperatorException {
            output = new Product("Wrong", "WrongType", 12, 12);
        }

        @Override
        public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        }

        public static class Spi extends OperatorSpi {

            public Spi() {
                super(WrongTypeOperator.class, "WrongTypeOperator");
            }

        }
    }

    public static class WrongBandsOperator extends Operator {
        @TargetProduct
        private Product targetProduct;
        
        @Override
        public void initialize() throws OperatorException {
            targetProduct = new Product("WrongBands", "GoodType", 1, 1);
            targetProduct.addBand("x", ProductData.TYPE_INT8);
            targetProduct.addBand("y", ProductData.TYPE_INT8);
        }

        @Override
        public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        }

        public static class Spi extends OperatorSpi {

            public Spi() {
                super(WrongBandsOperator.class, "WrongBandsOperator");
            }
        }
    }

    public static class GoodOperator extends Operator {
        @TargetProduct
        private Product targetProduct;
        
        @Override
        public void initialize() throws OperatorException {
            targetProduct = new Product("Good", "GoodType", 1, 1);
            targetProduct.addBand("a", ProductData.TYPE_INT8);
            targetProduct.addBand("b", ProductData.TYPE_INT8);
        }

        @Override
        public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        }

        public static class Spi extends OperatorSpi {

            public Spi() {
                super(GoodOperator.class, "GoodOperator");
            }
        }
    }

    public static class ConsumerOperator extends Operator {

        @SourceProduct(type = "GoodType", bands = {"a", "b"})
        Product input1;

        @TargetProduct
        Product output;

        @Override
        public void initialize() throws OperatorException {
            output = new Product("output", "outputType", 12, 12);
        }

        @Override
        public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        }

        public static class Spi extends OperatorSpi {

            public Spi() {
                super(ConsumerOperator.class, "ConsumerOperator");
            }
        }
    }

    public static class OptionalConsumerOperator extends Operator {

        @SourceProduct(optional = true, type = "Optional", bands = {"c", "d"})
        Product input1;

        @TargetProduct
        Product output;

        @Override
        public void initialize() throws OperatorException {
            output = new Product("output", "outputType", 1, 1);
        }

        @Override
        public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        }

        public static class Spi extends OperatorSpi {

            public Spi() {
                super(OptionalConsumerOperator.class, "OptionalConsumerOperator");
            }

        }
    }

    public static class ConsumerWithAliasSourceOperator extends Operator {

        @SourceProduct(alias = "alias")
        Product input1;

        @TargetProduct
        Product output;

        @Override
        public void initialize() throws OperatorException {
            output = new Product("output", "outputType", 1, 1);
        }

        @Override
        public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        }

        public static class Spi extends OperatorSpi {

            public Spi() {
                super(ConsumerWithAliasSourceOperator.class, "ConsumerWithAliasSourceOperator");
            }
        }
    }
}