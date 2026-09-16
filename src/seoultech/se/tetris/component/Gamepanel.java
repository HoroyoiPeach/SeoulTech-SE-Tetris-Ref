package seoultech.se.tetris.component;

import java.awt.*;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.CompoundBorder;

public class Gamepanel extends JPanel{
    private Board board;

    public Gamepanel(Maincontainer maincontainer) {
        setLayout(new GridBagLayout());
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        board = new Board();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.55; gbc.weighty = 0.0;
        add(board, gbc);

        JPanel emptypanel = new JPanel();
        emptypanel.setOpaque(false);
        gbc.gridx = 1; gbc.weightx = 0.05;
        add(emptypanel, gbc);

        JPanel nonboard = new JPanel();
        nonboard.setLayout(new GridBagLayout());
        nonboard.setOpaque(false);
        gbc.gridx = 2; gbc.weightx = 0.4;
        add(nonboard, gbc);

        JPanel nextblockpanel = new JPanel();
        nextblockpanel.setBackground(Color.BLACK);
		CompoundBorder border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Color.GRAY, 10),
				BorderFactory.createLineBorder(Color.DARK_GRAY, 5));
		nextblockpanel.setBorder(border);
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.weighty = 0.25;
        gbc.insets = new Insets(0, 0, 10, 0);
        nonboard.add(nextblockpanel, gbc);
        // TODO: score 내용 채우기
        JTextPane scorepanel = new JTextPane();
        gbc.gridy = 1; gbc.weighty = 0.15;
        gbc.insets = new Insets(10, 0, 10, 0);
        nonboard.add(scorepanel, gbc);

        JPanel emptypanel2 = new JPanel();
        emptypanel2.setOpaque(false);
        gbc.gridy = 2; gbc.weighty = 0.6;
        nonboard.add(emptypanel2, gbc);
    }
    @Override
    public boolean requestFocusInWindow() {
        return this.board.requestFocusInWindow();
    }
}
