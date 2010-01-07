package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;
import org.esa.nest.gpf.RangeDopplerGeocodingOp;

/**
 * Range-Doppler-Geocoding action.
 *
 */
public class RangeDopplerGeocodingOpAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        NestSingleTargetProductDialog dialog = new NestSingleTargetProductDialog(
                    "Terrain-Correction", getAppContext(), "Terrain-Correction", getHelpId());
        dialog.setTargetProductNameSuffix(RangeDopplerGeocodingOp.PRODUCT_SUFFIX);
        dialog.show();

    }
}