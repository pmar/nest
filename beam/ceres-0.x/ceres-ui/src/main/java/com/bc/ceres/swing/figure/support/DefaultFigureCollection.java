package com.bc.ceres.swing.figure.support;

import com.bc.ceres.grender.Rendering;
import com.bc.ceres.swing.figure.AbstractFigure;
import com.bc.ceres.swing.figure.AbstractFigureChangeListener;
import com.bc.ceres.swing.figure.Figure;
import com.bc.ceres.swing.figure.FigureChangeEvent;
import com.bc.ceres.swing.figure.FigureCollection;
import com.bc.ceres.swing.figure.Handle;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class DefaultFigureCollection extends AbstractFigure implements FigureCollection {

    private List<Figure> figureList;
    private Set<Figure> figureSet;
    private Rectangle2D bounds;
    private ChangeDelegate changeDelegate;

    public DefaultFigureCollection() {
        this(new Figure[0]);
    }

    public DefaultFigureCollection(Figure[] figures) {
        List<Figure> list = Arrays.asList(figures);
        init(list);
    }

    private void init(List<Figure> list) {
        this.figureList = new ArrayList<Figure>(list);
        this.figureSet = new HashSet<Figure>(list);
        this.changeDelegate = new ChangeDelegate();
        addChangeListener(new BoundsUpdater());
    }

    @Override
    public boolean isSelectable() {
        return true; 
    }

    @Override
    public boolean isSelected() {
        if (getFigureCount() == 0) {
            return false;
        }
        for (Figure figure : figureList) {
            if (!figure.isSelected()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setSelected(boolean selected) {
        if (isSelectable()) {
            for (Figure figure : figureList) {
                if (figure.isSelectable()) {
                    figure.setSelected(selected);
                }
            }
        }
    }

    @Override
    public Rank getRank() {
        return Rank.COLLECTION;
    }

    @Override
    public synchronized void dispose() {
        removeFiguresImpl();
        super.dispose();
    }

    @Override
    public synchronized Object clone() {
        final DefaultFigureCollection figureCollection = (DefaultFigureCollection) super.clone();
        figureCollection.init(figureList);
        return figureCollection;
    }

    @Override
    public int getMaxSelectionStage() {
        return 1;
    }

    @Override
    public synchronized Handle[] createHandles(int selectionStage) {
        if (getFigureCount() == 0) {
            return NO_HANDLES;
        } else if (getFigureCount() == 1) {
            return getFigure(0).createHandles(selectionStage);
        } else {
            return createScaleHandles(0.0);
        }
    }

    @Override
    public boolean isCloseTo(Point2D point, AffineTransform m2v) {
        return getBounds().contains(point);
    }

    @Override
    public synchronized boolean contains(Figure figure) {
        return figureSet.contains(figure);
    }

    @Override
    public synchronized int getFigureCount() {
        return figureList.size();
    }

    @Override
    public int getFigureIndex(Figure figure) {
        return figureList.indexOf(figure);
    }

    @Override
    public synchronized Figure getFigure(int index) {
        return figureList.get(index);
    }

    @Override
    public synchronized Figure getFigure(Point2D p, AffineTransform m2v) {
        Figure selectedShape = null;
        for (Figure figure : getFigures()) {
            if (figure.isCloseTo(p, m2v)) {
                selectedShape = figure;
            }
        }
        return selectedShape;
    }

    @Override
    public synchronized Figure[] getFigures(Shape shape) {
        ArrayList<Figure> containedFigures = new ArrayList<Figure>(getFigureCount());
        for (Figure figure : getFigures()) {
            if (shape.contains(figure.getBounds())) {
                containedFigures.add(figure);
            }
        }
        return containedFigures.toArray(new Figure[containedFigures.size()]);
    }

    @Override
    public synchronized Figure[] getFigures() {
        return figureList.toArray(new Figure[figureList.size()]);
    }

    @Override
    protected boolean addFigureImpl(Figure figure) {
        return addFigureImpl(getFigureCount(), figure);
    }

    @Override
    protected synchronized boolean addFigureImpl(int index, Figure figure) {
        figureSet.add(figure);
        figureList.add(index, figure);
        figure.addChangeListener(changeDelegate);
        return true;
    }

    @Override
    protected synchronized boolean removeFigureImpl(Figure figure) {
        boolean b = figureSet.remove(figure);
        if (b) {
            figureList.remove(figure);
            figure.removeChangeListener(changeDelegate);
        }
        return b;
    }

    @Override
    protected synchronized Figure[] removeFiguresImpl() {
        Figure[] allFigures = getFigures();
        for (Figure figure : allFigures) {
            figure.removeChangeListener(changeDelegate);
        }
        figureSet.clear();
        figureList.clear();
        return allFigures;
    }

    @Override
    public synchronized Rectangle2D getBounds() {
        if (bounds == null) {
            bounds = computeBounds();
        }
        return bounds;
    }

    @Override
    public synchronized void move(double dx, double dy) {
        if (getFigureCount() > 0) {
            for (Figure figure : figureList) {
                figure.move(dx, dy);
            }
            fireFigureChanged();
        }
    }

    @Override
    public synchronized void scale(Point2D refPoint, double sx, double sy) {
        if (getFigureCount() > 0) {
            for (Figure figure : figureList) {
                figure.scale(refPoint, sx, sy);
            }
            fireFigureChanged();
        }
    }

    @Override
    public synchronized void rotate(Point2D point, double theta) {
        if (getFigureCount() > 0) {
            for (Figure figure : figureList) {
                figure.rotate(point, theta);
            }
            fireFigureChanged();
        }
    }

    @Override
    public synchronized void draw(Rendering rendering) {
        for (Figure figure : figureList) {
            figure.draw(rendering);
        }
    }

    @Override
    public synchronized Object createMemento() {
        if (getFigureCount() == 0) {
            return null;
        }
        Map<Figure, Object> mementoMap = new HashMap<Figure, Object>(figureList.size());
        for (Figure figure : figureList) {
            mementoMap.put(figure, figure.createMemento());
        }
        return mementoMap;
    }

    @Override
    public synchronized void setMemento(Object memento) {
        if (memento != null) {
            Map<Figure, Object> mementoMap = (Map<Figure, Object>) memento;
            for (Entry<Figure, Object> entry : mementoMap.entrySet()) {
                Figure figure = entry.getKey();
                figure.setMemento(entry.getValue());
            }
            fireFigureChanged();
        }
    }

    protected synchronized Rectangle2D computeBounds() {
        final Rectangle2D bounds = new Rectangle2D.Double();
        if (getFigureCount() > 0) {
            bounds.setRect(figureList.get(0).getBounds());
            for (int i = 1; i < figureList.size(); i++) {
                Figure figure = figureList.get(i);
                bounds.add(figure.getBounds());
            }
        }
        return bounds;
    }

    private synchronized void nullBounds() {
        bounds = null;
    }

    private class BoundsUpdater extends AbstractFigureChangeListener {
        @Override
        public void figureChanged(FigureChangeEvent e) {
            nullBounds();
        }
    }

    private class ChangeDelegate extends AbstractFigureChangeListener {
        @Override
        public void figureChanged(FigureChangeEvent e) {
            fireFigureChanged();
        }
    }
}