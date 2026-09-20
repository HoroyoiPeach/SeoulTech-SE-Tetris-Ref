package seoultech.se.tetris.component;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

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
    private JTextPane scorepanel;
    private StyledDocument docScore;
    private SimpleAttributeSet styleSetScore1;
    private SimpleAttributeSet styleSetScore2;
    private Maincontainer maincontainer;
    private int score;
    private String usrname = "Player";
    private Path path;

    public Gamepanel(Maincontainer maincontainer) {
        setLayout(new GridBagLayout());
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        this.maincontainer = maincontainer;
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

        scorepanel = new JTextPane();
		scorepanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        scorepanel.setOpaque(false);
        scorepanel.setText("Scores\n" + this.score);
        docScore = scorepanel.getStyledDocument();
        styleSetScore1 = new SimpleAttributeSet();
        styleSetScore2 = new SimpleAttributeSet();
		StyleConstants.setFontSize(styleSetScore1, 18);
		StyleConstants.setFontFamily(styleSetScore1, "Courier");
		StyleConstants.setForeground(styleSetScore1, Color.GRAY);
		StyleConstants.setAlignment(styleSetScore1, StyleConstants.ALIGN_CENTER);
		docScore.setParagraphAttributes(0, 1, styleSetScore1, false);
        StyleConstants.setFontSize(styleSetScore2, 18);
		StyleConstants.setFontFamily(styleSetScore2, "Courier");
		StyleConstants.setForeground(styleSetScore2, Color.RED);
		StyleConstants.setAlignment(styleSetScore2, StyleConstants.ALIGN_RIGHT);
        docScore.setParagraphAttributes(7, 1, styleSetScore2, false);
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
    
    protected void drawNextBoard() {
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
		StyleConstants.setFontFamily(styleSet, "Monospaced");
		StyleConstants.setBold(styleSet, true);
        StyleConstants.setLineSpacing(styleSet, -0.1f);
		StyleConstants.setForeground(styleSet, this.board.next.getColor());
		StyleConstants.setAlignment(styleSet, StyleConstants.ALIGN_CENTER);
		doc.setParagraphAttributes(0, doc.getLength(), styleSet, false);
		this.nextblockpanel.setStyledDocument(doc);
	}

    protected void drawScore() {
        this.score = board.score;
        this.scorepanel.setText("Scores\n" + this.score);
        docScore = scorepanel.getStyledDocument();
		docScore.setParagraphAttributes(0, 1, styleSetScore1, false);
        docScore.setParagraphAttributes(7, 1, styleSetScore2, false);
    }

    protected void gameOver() {
        saveScore();
        maincontainer.exitGameEnterStart();
    }

    protected void saveScore() {
        path = Paths.get("tetris_score.txt");
        String str = "\n" + this.usrname + ":" + this.score;
        try {
            Files.write(path, Arrays.asList(str), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND); // 파일이 없으면 새로 만들고(CREATE), 있으면 맨 뒤에 이어 붙임(APPEND)
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
