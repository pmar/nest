package org.esa.nest.dat.actions.productLibrary.model.dataprovider;

import org.esa.nest.db.ProductEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;

public class IDProvider implements DataProvider {

    private final Comparator comparator = new IntComparator();
    private TableColumn column;

    public boolean mustCreateData(final ProductEntry entry) {
        return false;
    }

    public void createData(final ProductEntry entry) throws IOException {
    }

    public Object getData(final ProductEntry entry) throws IOException {
        return entry.getId();
    }

    public Comparator getComparator() {
        return comparator;
    }

    public void cleanUp(final ProductEntry entry) {
    }

    public TableColumn getTableColumn() {
        if(column == null) {
            column = new TableColumn();
            column.setHeaderValue("ID");
            column.setPreferredWidth(35);
            column.setResizable(false);
            column.setCellRenderer(new IDCellRenderer());
        }
        return column;
    }

    private static class IDCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(final JTable table,
                                                       final Object value,
                                                       final boolean isSelected,
                                                       final boolean hasFocus,
                                                       final int row, final int column) {
            final ProductEntry entry = (ProductEntry) value;
            if(entry != null) {
                final String text = String.valueOf(entry.getId());

                final JLabel jlabel = (JLabel) super
                        .getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);

                jlabel.setFont(jlabel.getFont().deriveFont(Font.BOLD));
                jlabel.setToolTipText(text);
                return jlabel;
            }
            return null;
        }
    }

    private static class IntComparator implements Comparator {

        public int compare(final Object o1, final Object o2) {
            if(o1 == o2) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            } else if(o2 == null) {
                return 1;
            }

            final int s1 = (Integer) o1;
            final int s2 = (Integer) o2;

            if(s1 < s2)
                return -1;
            return 1;
        }
    }
}