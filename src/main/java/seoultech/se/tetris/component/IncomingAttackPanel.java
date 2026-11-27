package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import seoultech.se.tetris.attack.AttackQueue;
import seoultech.se.tetris.attack.AttackRow;

/** Displays the exact pending attack shape using text as well as color. */
final class IncomingAttackPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel title = new JLabel("INCOMING 0/10", SwingConstants.CENTER);
    private final JTextArea rows = new JTextArea(AttackQueue.MAX_ROWS, 0);

    IncomingAttackPanel(String componentPrefix) {
        super(new BorderLayout(0, 3));
        setOpaque(false);
        setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY)));
        title.setName(componentPrefix + "Incoming");
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        title.setForeground(changeColor(Color.RED));
        rows.setName(componentPrefix + "IncomingRows");
        rows.setEditable(false);
        rows.setFocusable(false);
        rows.setOpaque(false);
        rows.setFont(new Font(Font.MONOSPACED, Font.BOLD, 8));
        rows.setForeground(changeColor(Color.GRAY));
        add(title, BorderLayout.NORTH);
        add(rows, BorderLayout.CENTER);
        showRows(List.of());
    }

    void refreshTheme() {
        setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY)));
        title.setForeground(changeColor(Color.RED));
        rows.setForeground(changeColor(Color.GRAY));
        repaint();
    }

    void showRows(List<AttackRow> attacks) {
        List<AttackRow> safeRows = List.copyOf(attacks);
        title.setText("INCOMING " + safeRows.size() + "/" + AttackQueue.MAX_ROWS);
        StringBuilder shapes = new StringBuilder();
        for (AttackRow attack : safeRows) {
            if (!shapes.isEmpty()) shapes.append('\n');
            for (int column = 0; column < attack.width(); column++) {
                shapes.append(attack.occupiedAt(column) ? 'G' : '.');
            }
        }
        rows.setText(shapes.toString());
    }

    JLabel title() {
        return title;
    }

    JTextArea rows() {
        return rows;
    }
}
