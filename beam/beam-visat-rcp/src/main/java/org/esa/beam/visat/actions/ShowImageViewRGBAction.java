/*
 * $Id: ShowImageViewRGBAction.java,v 1.4 2009-05-25 13:29:17 lveci Exp $
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
package org.esa.beam.visat.actions;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RGBImageProfile;
import org.esa.beam.framework.ui.RGBImageProfilePane;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.ui.product.ProductSceneImage;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.visat.VisatApp;

import javax.swing.Icon;
import javax.swing.JInternalFrame;
import javax.swing.SwingWorker;
import java.awt.Cursor;

/**
 * This action opens an RGB image view on the currently selected Product.
 *
 * @author Marco Peters
 * @version $Revision: 1.4 $ $Date: 2009-05-25 13:29:17 $
 */
public class ShowImageViewRGBAction extends ExecCommand {

    public static String ID = "showImageViewRGB";

    @Override
    public void actionPerformed(final CommandEvent event) {
        final Product product = VisatApp.getApp().getSelectedProduct();
        if (product != null) {
            openProductSceneViewRGB(product, getHelpId());
        }
    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(VisatApp.getApp().getSelectedProduct() != null);
    }

    public void openProductSceneViewRGB(final Product product, final String helpId) {
        final VisatApp visatApp = VisatApp.getApp();
        final RGBImageProfilePane profilePane = new RGBImageProfilePane(visatApp.getPreferences(), product);
        final String title = visatApp.getAppName() + " - Select RGB-Image Channels";
        final boolean ok = profilePane.showDialog(visatApp.getMainFrame(), title, helpId);
        if (!ok) {
            return;
        }
        final String[] rgbaExpressions = profilePane.getRgbaExpressions();
        if (profilePane.getStoreProfileInProduct()) {
            RGBImageProfile.storeRgbaExpressions(product, rgbaExpressions);
        }

        final String sceneName = createSceneName(product, profilePane.getSelectedProfile());
        openProductSceneViewRGB(sceneName, product, rgbaExpressions);
    }

    /**
     * Creates product scene view using the given RGBA expressions.
     */
    public void openProductSceneViewRGB(final String name, final Product product, final String[] rgbaExpressions) {
        final VisatApp visatApp = VisatApp.getApp();
        final SwingWorker<ProductSceneImage, Object> worker = new ProgressMonitorSwingWorker<ProductSceneImage, Object>(
                visatApp.getMainFrame(),
                visatApp.getAppName() + " - Creating image for '" + name + "'") {

            @Override
            protected ProductSceneImage doInBackground(ProgressMonitor pm) throws Exception {
                return createProductSceneImageRGB(name, product, rgbaExpressions, pm);
            }

            @Override
            protected void done() {
                visatApp.getMainFrame().setCursor(Cursor.getDefaultCursor());

                final ProductSceneImage productSceneImage;
                try {
                    productSceneImage = get();
                } catch (OutOfMemoryError e) {
                    visatApp.showOutOfMemoryErrorDialog("The RGB image view could not be created."); /*I18N*/
                    return;
                } catch (Exception e) {
                    visatApp.handleUnknownException(e);
                    return;
                }
                visatApp.clearStatusBarMessage();

                ProductSceneView productSceneView = new ProductSceneView(productSceneImage);
                productSceneView.setNoDataOverlayEnabled(false);
                productSceneView.setROIOverlayEnabled(false);
                productSceneView.setGraticuleOverlayEnabled(false);
                productSceneView.setPinOverlayEnabled(false);
                productSceneView.setLayerProperties(visatApp.getPreferences());
                openInternalFrame(productSceneView);
            }
        };
        visatApp.setStatusBarMessage("Creating RGB image view...");  /*I18N*/
        visatApp.getExecutorService().submit(worker);
    }

    public JInternalFrame openInternalFrame(final ProductSceneView view) {
        return openInternalFrame(view, true);
    }

    public JInternalFrame openInternalFrame(ProductSceneView view, boolean configureByPreferences) {
        final VisatApp visatApp = VisatApp.getApp();
        view.setCommandUIFactory(visatApp.getCommandUIFactory());
        if (configureByPreferences) {
            view.setLayerProperties(visatApp.getPreferences());
        }

        final String title = createUniqueInternalFrameTitle(view.getSceneName());
        final Icon icon = UIUtils.loadImageIcon("icons/RsBandAsSwath16.gif");
        final JInternalFrame internalFrame = visatApp.createInternalFrame(title, icon, view, getHelpId());
        visatApp.addPropertyMapChangeListener(view);
        updateState();

        return internalFrame;
    }


    private static class RGBBand {

        private Band band;
        private boolean dataLoaded;
    }

    private ProductSceneImage createProductSceneImageRGB(String name, final Product product, String[] rgbaExpressions,
                                                         ProgressMonitor pm) throws Exception {
        final VisatApp visatApp = VisatApp.getApp();
        UIUtils.setRootFrameWaitCursor(visatApp.getMainFrame());
        RGBBand[] rgbBands = null;
        boolean errorOccured = false;
        ProductSceneImage productSceneImage = null;
        try {
            pm.beginTask("Creating RGB image...", 2);
            rgbBands = allocateRgbBands(product, rgbaExpressions);
            productSceneImage = new ProductSceneImage(name, rgbBands[0].band,
                                                      rgbBands[1].band,
                                                      rgbBands[2].band,
                                                      visatApp.getPreferences(),
                                                      SubProgressMonitor.create(pm, 1));
        } catch (Exception e) {
            errorOccured = true;
            throw e;
        } finally {
            pm.done();
            if (rgbBands != null) {
                releaseRgbBands(rgbBands, errorOccured);
            }
        }
        return productSceneImage;
    }

    private RGBBand[] allocateRgbBands(final Product product, final String[] rgbaExpressions) {
        final RGBBand[] rgbBands = new RGBBand[3]; // todo - set to [4] as soon as we support alpha
        final boolean productModificationState = product.isModified();
        for (int i = 0; i < rgbBands.length; i++) {
            final RGBBand rgbBand = new RGBBand();
            String expression = rgbaExpressions[i].isEmpty() ? "0" : rgbaExpressions[i];
            rgbBand.band = product.getBand(expression);
            if (rgbBand.band == null) {
                rgbBand.band = new ProductSceneView.RGBChannel(product,
                                                               RGBImageProfile.RGB_BAND_NAMES[i],
                                                               expression);
            }
            rgbBands[i] = rgbBand;
        }
        product.setModified(productModificationState);
        return rgbBands;
    }

    private static void releaseRgbBands(RGBBand[] rgbBands, boolean errorOccured) {
        for (int i = 0; i < rgbBands.length; i++) {
            final RGBBand rgbBand = rgbBands[i];
            if (rgbBand != null && rgbBand.band != null) {
                if (rgbBand.band instanceof ProductSceneView.RGBChannel) {
                    if (rgbBand.dataLoaded) {
                        rgbBand.band.unloadRasterData();
                    }
                    if (errorOccured) {
                        rgbBand.band.dispose();
                    }
                }
                rgbBand.band = null;
            }
            rgbBands[i] = null;
        }
    }

    private static String createUniqueInternalFrameTitle(String name) {
        return UIUtils.getUniqueFrameTitle(VisatApp.getApp().getAllInternalFrames(), name);
    }

    private static String createSceneName(Product product, RGBImageProfile rgbImageProfile) {
        final StringBuilder nameBuilder = new StringBuilder();
        final String productRef = product.getProductRefString();
        if (productRef != null) {
            nameBuilder.append(productRef);
            nameBuilder.append(" ");
        }
        if (rgbImageProfile != null) {
            nameBuilder.append(rgbImageProfile.getName().replace("_", " "));
            nameBuilder.append(" ");
        }
        nameBuilder.append("RGB");

        return nameBuilder.toString();
    }
}