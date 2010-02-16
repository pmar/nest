
package org.esa.nest.dat;

import com.bc.ceres.core.Assert;
import org.esa.beam.framework.ui.ModalDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * This class pertains to the "about" dialog box for the VISAT application.
 */
class DatAboutBox extends ModalDialog {

    public DatAboutBox() {
        this(new JButton[]{
                new JButton(),
                new JButton(),
        });
    }

    private DatAboutBox(JButton[] others) {
        super(DatApp.getApp().getMainFrame(), "About DAT", ModalDialog.ID_OK, others, null);    /*I18N*/

        final JButton creditsButton = others[0];
        creditsButton.setText("Credits...");  /*I18N*/
        creditsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showCreditsDialog();
            }
        });

        final JButton systemButton = others[1];
        systemButton.setText("System Info...");  /*I18N*/
        systemButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showSystemDialog();
            }
        });

        final URL resource = getClass().getResource("/about_nest.jpg");
        Assert.notNull(resource);
        final Icon icon = new ImageIcon(resource);

        final JLabel imageLabel = new JLabel(icon);
        final JPanel dialogContent = new JPanel(new BorderLayout());
        final String versionText = getVersionHtml();
        final JLabel versionLabel = new JLabel(versionText);

        final JPanel labelPane = new JPanel(new BorderLayout());
        labelPane.add(BorderLayout.NORTH, versionLabel);

        dialogContent.setLayout(new BorderLayout(4, 4));
        dialogContent.add(BorderLayout.WEST, imageLabel);
        dialogContent.add(BorderLayout.EAST, labelPane);

        setContent(dialogContent);
    }

    @Override
    protected void onOther() {
        // override default behaviour by doing nothing
    }

    private void showCreditsDialog() {
        final ModalDialog modalDialog = new ModalDialog(getJDialog(), "Credits", ID_OK, null); /*I18N*/
        final String credits = getCreditsHtml();
        final JLabel creditsPane = new JLabel(credits); /*I18N*/
        modalDialog.setContent(creditsPane);
        modalDialog.show();
    }


    private void showSystemDialog() {
        final ModalDialog modalDialog = new ModalDialog(getJDialog(), "System Info", ID_OK, null);
        final Object[][] sysInfo = getSystemInfo();
        final JTable sysTable = new JTable(sysInfo, new String[]{"Property", "Value"}); /*I18N*/
        final JScrollPane systemScroll = new JScrollPane(sysTable);
        systemScroll.setPreferredSize(new Dimension(400, 400));
        modalDialog.setContent(systemScroll);
        modalDialog.show();
    }

    private static String getVersionHtml() {
        final String ver = System.getProperty("nest.version");
        final String pattern =
                "<html>" +
                "<b>NEST DAT Version "+ver+"</b>" +
                "<br>(c) Copyright 2007-2010 by Array Systems Computing Inc. and contributors. All rights reserved." +
                "<br>Visit http://www.array.ca/nest" +
                "<br>" +
                "<b>BEAM </b>" +
                "<br>(c) Copyright 2002-2010 by Brockmann Consult and contributors. All rights reserved." +
                "<br>Visit http://www.brockmann-consult.de/beam/" +
                "<br>" +
                "<br>This program has been developed under contract to ESA (ESRIN)." +
                "<br>Visit http://envisat.esa.int/services/" +
                "<br>" +
                "<br>This program is free software; you can redistribute it and/or modify it" +
                "<br>under the terms of the GNU General Public License as published by the" +
                "<br>Free Software Foundation. This program is distributed in the hope it will be" +
                "<br>useful, but WITHOUT ANY WARRANTY; without even the implied warranty" +
                "<br>of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE." +
                "<br>See the GNU General Public License for more details." +
                "<br>" +
                "<br>This product includes software developed by Unidata and NCSA" +
                "<br>Visit http://www.unidata.ucar.edu/ and http://hdf.ncsa.uiuc.edu/" +
                "</html>";
        return pattern; /*I18N*/
    }

    private static String getCreditsHtml() {
        return
                "<html>" +
                "<br>The NEST team at Array Systems Computing is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Rajesh Jha</b> (project manager)<br>" +
                "&nbsp;&nbsp;<b>Luis Veci</b> (software lead)<br>" +
                "&nbsp;&nbsp;<b>Jun Lu</b> (scientist/developer)<br>" +
                "&nbsp;&nbsp;<b>Shengli Dai</b> (project scientist)<br>" +
                "</td><td>" +
                "&nbsp;&nbsp;<b>Andrew Taylor</b> (IT support)<br>" +
                "&nbsp;&nbsp;<b>Iris Buchan</b> (quality assurance)<br>" +
                "&nbsp;&nbsp;<b>Nisso Keslassy</b> (contracts officer)<br>" +
                "&nbsp;&nbsp;<b></b> <br>" +
                "</td></tr>" +
                "</table>" +
                "<br><hr>The BEAM team at Brockmann Consult is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Tom Block</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Carsten Brockmann</b> (quality control)<br>" +
                "&nbsp;&nbsp;<b>Sabine Embacher</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Olga Faber</b> (testing)<br>" +
                "&nbsp;&nbsp;<b>Norman Fomferra</b> (project lead)<br>" +
                "&nbsp;&nbsp;<b>Uwe Kr�mer</b> (Mac OS X porting)<br>" +
                "</td><td>" +
                "&nbsp;&nbsp;<b>Des Murphy</b> (contract management)<br>" +
                "&nbsp;&nbsp;<b>Michael Paperin</b> (web development)<br>" +
                "&nbsp;&nbsp;<b>Marco Peters</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Ralf Quast</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Kerstin Stelzer</b> (quality control)<br>" +
                "&nbsp;&nbsp;<b>Marco Z�hlke</b> (programming)<br>" +
                "</td></tr>" +
                "</table>" +
                 "<br><hr>The NEST-DORIS team at PPO.Labs/TU Delft is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Ramon Hanssen</b> (project manager)<br>" +
                "&nbsp;&nbsp;<b>Petar Marinkovic</b> (software lead/scientist)<br>" +
                "</td></tr>" +
                "</table>" +
                "<br><hr>The NEST team at ESA/ESRIN is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Marcus Engdahl</b> (technical officer)<br>" +
                "&nbsp;&nbsp;<b>Andrea Minchella</b> (scientist)<br>" +
                "&nbsp;&nbsp;<b>Romain Husson</b> (scientist)<br>" +
                "</td></tr>" +
                "</table>" +
                "<br><hr>The NEST developers would also like to say thank you to" +
                "<br>&nbsp;&nbsp;<b>Sun</b> for the beautiful programming language they have invented," +
                "<br>&nbsp;&nbsp;<b>NASA</b> for the wonderful WorldWind Java SDK," +        
                "<br>&nbsp;&nbsp;<b>IntelliJ</b> for the best IDE in the world," +
                "<br>&nbsp;&nbsp;<b>Eclipse.org</b> for the second best IDE in the world," +
                "<br>&nbsp;&nbsp;<b>JIDE Software</b> for a great docking framework," +
                "<br>&nbsp;&nbsp;all companies and organisations supporting the open-source idea." +
                "<br><br><hr>" +
                "</html>"; /*I18N*/
    }

    private static Object[][] getSystemInfo() {

        final List<Object[]> data = new ArrayList<Object[]>();

        Properties sysProps = null;
        try {
            sysProps = System.getProperties();
        } catch (RuntimeException e) {
        }
        if (sysProps != null) {
            final String[] names = new String[sysProps.size()];
            final Enumeration<?> e = sysProps.propertyNames();
            for (int i = 0; i < names.length; i++) {
                names[i] = (String) e.nextElement();
            }
            Arrays.sort(names);
            for (String name : names) {
                final String value = sysProps.getProperty(name);
                data.add(new Object[]{name, value});
            }
        }

        final Object[][] dataArray = new Object[data.size()][2];
        for (int i = 0; i < dataArray.length; i++) {
            dataArray[i] = data.get(i);
        }
        return dataArray;
    }
}
