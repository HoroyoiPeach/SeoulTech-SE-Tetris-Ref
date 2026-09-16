package seoultech.se.tetris.component;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
// import java.awt.*;
import javax.swing.JPanel;

public class Gamepanel extends JPanel{
    public Gamepanel(Maincontainer maincontainer) {
        setLayout(new GridBagLayout());
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        Board board = new Board();
        GridBagConstraints boardgbc = new GridBagConstraints();
        boardgbc.gridx = 0; boardgbc.gridy = 0;
        boardgbc.fill = GridBagConstraints.NONE;
        boardgbc.weightx = 0.0; boardgbc.weighty = 0.0;

        add(board, boardgbc);
        // TODO: Board를 비롯한 게임 구성 판넬을 합치기
    }
}
