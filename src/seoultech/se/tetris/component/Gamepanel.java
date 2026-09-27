package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import seoultech.se.tetris.blocks.Block;

public class Gamepanel extends JPanel{
    private Board board;
    private Nextblockpanel nextblockpanel;
    private JTextPane scorepanel;
    private StyledDocument docScore;
    private SimpleAttributeSet styleSetScore1;
    private SimpleAttributeSet styleSetScore2;
    private Maincontainer maincontainer;
    private int score;
    private String usrname = "Player";
    private Path path;
    private JPanel pausepanel;
    private Integer[] h_w;
    private int HEIGHT;
    private int WIDTH;

    public Gamepanel(Maincontainer maincontainer) {
        setLayout(null);
        setBackground(changeColor(Color.LIGHT_GRAY));
        this.maincontainer = maincontainer;
        h_w = maincontainer.setting.getWindowSize();
        HEIGHT = h_w[0]; WIDTH = h_w[1];

        board = new Board(this);
        board.setBounds(10, 10, WIDTH*330/500, HEIGHT*580/600);
        add(board);

        nextblockpanel = new Nextblockpanel(this);
        nextblockpanel.setBounds(WIDTH*330/500+20, 10, WIDTH*140/500, HEIGHT*140/600);
        add(nextblockpanel);

        scorepanel = new JTextPane();
        scorepanel.setEditable(false);
        scorepanel.setBounds(WIDTH*330/500+20, HEIGHT*140/600+20, WIDTH*140/500, HEIGHT*50/600);
		scorepanel.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
        scorepanel.setOpaque(false);
        scorepanel.setText("Scores\n" + this.score);
        docScore = scorepanel.getStyledDocument();
        styleSetScore1 = new SimpleAttributeSet();
        styleSetScore2 = new SimpleAttributeSet();
		StyleConstants.setFontSize(styleSetScore1, 18);
		StyleConstants.setFontFamily(styleSetScore1, "Courier");
		StyleConstants.setForeground(styleSetScore1, changeColor(Color.GRAY));
		StyleConstants.setAlignment(styleSetScore1, StyleConstants.ALIGN_CENTER);
		docScore.setParagraphAttributes(0, 1, styleSetScore1, false);
        StyleConstants.setFontSize(styleSetScore2, 18);
		StyleConstants.setFontFamily(styleSetScore2, "Courier");
		StyleConstants.setForeground(styleSetScore2, changeColor(Color.RED));
		StyleConstants.setAlignment(styleSetScore2, StyleConstants.ALIGN_RIGHT);
        docScore.setParagraphAttributes(7, 1, styleSetScore2, false);
        add(scorepanel);

        board.boardStart();
    }

    @Override
    public boolean requestFocusInWindow() {
        return this.board.requestFocusInWindow();
    }
    
    protected void drawNextBoard() {
		nextblockpanel.drawNextBoard();
	}

    protected void drawScore() {
        this.score = board.getScore();
        this.scorepanel.setText("Scores\n" + this.score);
        docScore = scorepanel.getStyledDocument();
		docScore.setParagraphAttributes(0, 1, styleSetScore1, false);
        docScore.setParagraphAttributes(7, 1, styleSetScore2, false);
    }

    protected void gameOver() {
        saveScore();
        maincontainer.exitGameEnterStart();
    }

    private void saveScore() {
        path = Paths.get("tetris_score.txt");
        String str = this.usrname + ":" + this.score;
        try {
            Files.write(path, Arrays.asList(str), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND); // 파일이 없으면 새로 만들고(CREATE), 있으면 맨 뒤에 이어 붙임(APPEND)
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void gamePause() {
        pausepanel = new JPanel() {
		    @Override 
		    protected  void paintComponent(Graphics g) {
			    super.paintComponent(g);
			    Graphics2D g2d = (Graphics2D) g;
			    g2d.setColor(changeColor(new Color(255, 255, 255, 80)));
			    g2d.fillRect(0, 0, board.getWidth(), board.getHeight());
		    }
	    };
        pausepanel.setOpaque(false);
        pausepanel.setBounds(10, 10, board.getWidth(), board.getHeight());
        pausepanel.setLayout(new GridBagLayout());
        JPanel textpanel = new JPanel(new GridLayout(3, 1, 0, 10));
        textpanel.setOpaque(false);

            JLabel pause = new JLabel("- PAUSED -", SwingConstants.CENTER);
            pause.setFont(new Font("Courier", Font.BOLD, 20));
            pause.setForeground(changeColor(Color.WHITE));

            JLabel explane = new JLabel("press pause to resume", SwingConstants.CENTER);
            explane.setFont(new Font("Courier", Font.BOLD, 13));
            explane.setForeground(changeColor(Color.WHITE));

            JLabel exit = new JLabel("press enter to exit", SwingConstants.CENTER);
            exit.setFont(new Font("Courier", Font.BOLD, 13));
            exit.setForeground(changeColor(Color.WHITE));

        int h = pause.getPreferredSize().height + explane.getPreferredSize().height + exit.getPreferredSize().height;
        textpanel.setPreferredSize(new Dimension(board.getWidth(), h+20));

        textpanel.add(pause);
        textpanel.add(explane);
        textpanel.add(exit);
        pausepanel.add(textpanel);

        this.add(pausepanel);
        this.setComponentZOrder(pausepanel, 0);
        this.revalidate();
        this.repaint();
    }

    protected void gameRestart() {
        this.remove(pausepanel);
        revalidate();
        repaint();
    }

    protected Block getNextBlock() {
        return board.getNextBlock();
    }
}
