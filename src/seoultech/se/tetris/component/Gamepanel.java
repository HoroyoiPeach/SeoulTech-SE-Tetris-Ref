package seoultech.se.tetris.component;

import java.awt.*;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.CompoundBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class Gamepanel extends JPanel{
    private Board board;
    private JTextPane nextblockpanel;

    public Gamepanel(Maincontainer maincontainer) {
        setLayout(new GridBagLayout());
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        board = new Board(this);
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

        nextblockpanel = new JTextPane();
        nextblockpanel.setBackground(Color.BLACK);
        nextblockpanel.setAlignmentY(Component.CENTER_ALIGNMENT);
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

        board.boardStart();
    }
    @Override
    public boolean requestFocusInWindow() {
        return this.board.requestFocusInWindow();
    }
    public void refreshNextBlock() {
        drawNextBoard();
    }
    
    public void drawNextBoard() {
		StringBuffer sb = new StringBuffer();
		for(int i=0; i < this.board.next.height(); i++) {
            for(int j=0; j < this.board.next.width(); j++) {
                if(this.board.next.getShape(j, i) == 0) sb.append(" ");
                else sb.append("O");
            }
            if(i != this.board.next.width()-1) sb.append("\n");
        }
        this.nextblockpanel.setText(sb.toString());
		StyledDocument doc = this.nextblockpanel.getStyledDocument();
        SimpleAttributeSet styleSet = new SimpleAttributeSet();
        StyleConstants.setFontSize(styleSet, 18);
		StyleConstants.setFontFamily(styleSet, "Courier");
		StyleConstants.setBold(styleSet, true);
		StyleConstants.setForeground(styleSet, this.board.next.getColor());
		StyleConstants.setAlignment(styleSet, StyleConstants.ALIGN_CENTER);
		doc.setParagraphAttributes(0, doc.getLength(), styleSet, false);
		this.nextblockpanel.setStyledDocument(doc);
	}
}
