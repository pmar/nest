package org.esa.nest.dat.views.polarview;

import java.awt.Color;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.Enumeration;
import java.util.Vector;

public abstract class ColorScale
{

    protected ColorScale(Color colorTable[])
    {
        coloredClients = new Vector();
        thresholdCount = colorTable.length;
        colorIndexThresholds = new int[thresholdCount];
        colorIndexThresholds[0] = 0;
        colorIndexThresholds[thresholdCount - 1] = 255;
        colors = colorTable;
        setDefaultThresholds();
    }

    public static ColorScale newMonochromeScale(double range[], Color chromum)
    {
        Color monochromeColorTable[] = {
            Color.black, Color.black, chromum, chromum
        };
        return new DoubleColorScale(monochromeColorTable, range);
    }

    public static ColorScale newCustomScale(Color colorTable[], double range[])
    {
        return new DoubleColorScale(colorTable, range);
    }

    public abstract boolean isDirectIndex();

    public abstract boolean isIntegerValue();

    public abstract double getTotalRange();

    public abstract double[] getRange();

    public abstract void setRange(double ad[]);

    public final Color getColor(int value)
    {
        return new Color(getRGB(value));
    }

    public final Color getColor(float value)
    {
        return new Color(getRGB(value));
    }

    public final Color getColor(double value)
    {
        return new Color(getRGB(value));
    }

    public int getRGB(int value)
    {
        return cm.getRGB(getColorIndex(value) & 0xff);
    }

    public int getRGB(float value)
    {
        return cm.getRGB(getColorIndex(value) & 0xff);
    }

    public int getRGB(double value)
    {
        return cm.getRGB(getColorIndex(value) & 0xff);
    }

    public abstract byte getColorIndex(int i);

    public abstract byte getColorIndex(float f);

    public abstract byte getColorIndex(double d);

    public abstract int getIntegerColorValue(int i);

    public abstract float getFloatColorValue(int i);

    public abstract double getDoubleColorValue(int i);

    public abstract int getIntegerThresholdValue(int i);

    public abstract float getFloatThresholdValue(int i);

    public abstract double getDoubleThresholdValue(int i);

    public boolean isDirectRGB()
    {
        return false;
    }

    public int getThresholdCount()
    {
        return thresholdCount;
    }

    public int getThresholdIndex(int th)
    {
        return colorIndexThresholds[th];
    }

    public void resetColorThresholds()
    {
        if(defaultIndexThresholds == null)
        {
            return;
        } else
        {
            colorIndexThresholds = new int[thresholdCount];
            System.arraycopy(defaultIndexThresholds, 0, colorIndexThresholds, 0, thresholdCount);
            update();
            return;
        }
    }

    public void setDefaultThresholds()
    {
        defaultIndexThresholds = new int[thresholdCount];
        System.arraycopy(colorIndexThresholds, 0, defaultIndexThresholds, 0, thresholdCount);
    }

    public void setColorThreshold(int i, int index)
    {
        if(i < 1 || i >= thresholdCount - 1)
        {
            return;
        } else
        {
            index = limitColorThreshold(i, index);
            colorIndexThresholds[i] = index;
            update();
            return;
        }
    }

    public ColorModel getColorModel()
    {
        return cm;
    }

    public synchronized void addColoredObject(Colored ip)
    {
        if(coloredClients.contains(ip))
        {
            return;
        } else
        {
            coloredClients.addElement(ip);
            return;
        }
    }

    public synchronized void removeColoredObject(Colored ip)
    {
        coloredClients.removeElement(ip);
    }

    public int limitColorThreshold(int i, int index)
    {
        int lastThreshold = thresholdCount - 1;
        if(i < 1 || i >= lastThreshold)
            return index;
        int upperLimit = colorIndexThresholds[i + 1];
        if(i < lastThreshold - 1)
            upperLimit -= 2;
        int lowerLimit = colorIndexThresholds[i - 1];
        if(i > 1)
            lowerLimit += 2;
        index = Math.min(index, upperLimit);
        index = Math.max(index, lowerLimit);
        return index;
    }

    protected abstract void updateColorValues();

    protected void setEvenThresholds()
    {
        int N = thresholdCount - 1;
        int first = 0;
        int last = N;
        if(colors[last].equals(colors[last - 1]))
        {
            colorIndexThresholds[last] = 255;
            last--;
            N--;
        }
        if(colors[first].equals(colors[first + 1]))
        {
            colorIndexThresholds[first] = 0;
            first++;
            N--;
        }
        double colorStep = 255D;
        int offset = 0;
        if(isDirectIndex())
        {
            double range[] = getRange();
            offset = (int)Math.round(range[0]);
            colorStep = Math.min(colorStep, getTotalRange());
        } else
        if(isIntegerValue())
            colorStep = Math.min(colorStep, getTotalRange());
        colorStep /= N;
        int i = 0;
        for(int t = first; t <= last; t++)
        {
            colorIndexThresholds[t] = offset + (int)Math.round((double)i * colorStep);
            i++;
        }

    }

    protected void createColorMap()
    {
        int lastThreshold = thresholdCount - 1;
        byte cmap[] = new byte[768];
        Color lastColor = colors[0];
        int lastIndex = colorIndexThresholds[0];
        int c = 0;
        int k = 0;
        for(int i = 1; i < thresholdCount; i++)
        {
            int cRange = colorIndexThresholds[i] - lastIndex;
            int lastRGB[] = {
                lastColor.getRed(), lastColor.getGreen(), lastColor.getBlue()
            };
            int nextRGB[] = {
                colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue()
            };
            for(int j = 0; j < cRange; j++)
            {
                float nextScale = (float)j / (float)cRange;
                float lastScale = 1.0F - nextScale;
                k++;
                cmap[c++] = (byte)(int)((float)lastRGB[0] * lastScale + (float)nextRGB[0] * nextScale);
                cmap[c++] = (byte)(int)((float)lastRGB[1] * lastScale + (float)nextRGB[1] * nextScale);
                cmap[c++] = (byte)(int)((float)lastRGB[2] * lastScale + (float)nextRGB[2] * nextScale);
            }

            lastColor = colors[i];
            lastIndex = colorIndexThresholds[i];
        }

        Color finalColor = colors[lastThreshold];
        cmap[c++] = (byte)finalColor.getRed();
        cmap[c++] = (byte)finalColor.getGreen();
        cmap[c++] = (byte)finalColor.getBlue();
        cm = new IndexColorModel(8, 256, cmap, 0, false);
    }

    protected synchronized void notifyMapChange()
    {
        Colored ip;
        for(Enumeration elem = coloredClients.elements(); elem.hasMoreElements(); ip.updatedColorMap())
        {
            ip = (Colored)elem.nextElement();
        }

    }

    protected synchronized void notifyRangeChange()
    {
        Colored ip;
        for(Enumeration elem = coloredClients.elements(); elem.hasMoreElements(); ip.updatedColorScale())
        {
            ip = (Colored)elem.nextElement();
        }

    }

    protected void updateRange()
    {
        updateColorValues();
        createColorMap();
        notifyRangeChange();
    }

    private void update()
    {
        updateColorValues();
        createColorMap();
        notifyMapChange();
    }

    protected ColorModel cm;
    protected Color colors[];
    protected int thresholdCount;
    protected int colorIndexThresholds[];
    protected int defaultIndexThresholds[];
    private Vector coloredClients;
    private static Color greyColorTable[];
    private static Color thermalColorTable[];
    private static ColorScale defaultScale = null;

    static 
    {
        greyColorTable = (new Color[] {
            Color.black, Color.black, Color.white, Color.white
        });
        thermalColorTable = (new Color[] {
            Color.black, Color.blue, Color.cyan, Color.green, Color.yellow, Color.red, Color.white
        });
    }
}
