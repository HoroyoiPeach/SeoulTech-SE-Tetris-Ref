package seoultech.se.tetris.component;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Scoreboardpanel extends JPanel{
    private Path path;
    private int totalusernum;
    private int selected = 1;
    private ArrayList<JLabel> selectlist;
    private Maincontainer maincontainer;
    private ArrayList<GameScore> loadedScores;
    private JTextPane scorepanel;
    private PlayerKeyListener playerKeyListener;

    public Scoreboardpanel(Maincontainer maincontainer) {
        setLayout(null);
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        this.maincontainer = maincontainer;

        scorepanel = new JTextPane();
        scorepanel.setEditable(false);
        scorepanel.setBounds(10, 10, 480, 480);
        scorepanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        scorepanel.setOpaque(false);
        scorepanel.setMargin(new Insets(10, 10, 10, 10));
        drawScore();

        JPanel buttonpanel = new JPanel();
        buttonpanel.setBounds(100, 535, 300, 30);
        buttonpanel.setLayout(new GridLayout(1, 2, 80, 0));
        buttonpanel.setOpaque(false); // 투명 배경
        JLabel exitbutton = new JLabel("EXIT", SwingConstants.CENTER);
        exitbutton.setFont(new Font("Courier", Font.BOLD, 20));
        exitbutton.setForeground(Color.BLACK);
        exitbutton.setBackground(Color.GRAY);
        exitbutton.setOpaque(true);
        JLabel resetbutton = new JLabel("RESET", SwingConstants.CENTER);
        resetbutton.setFont(new Font("Courier", Font.BOLD, 20));
        resetbutton.setForeground(Color.BLACK);
        resetbutton.setBackground(Color.GRAY);
        resetbutton.setOpaque(false);
        buttonpanel.add(resetbutton);
        buttonpanel.add(exitbutton);
        selectlist = new ArrayList<JLabel>(List.of(resetbutton, exitbutton));

        add(scorepanel);
        add(buttonpanel);

        playerKeyListener = new PlayerKeyListener();
		addKeyListener(playerKeyListener);
		setFocusable(true);
		requestFocus();
    }

    protected class GameScore implements Comparable<GameScore> {
        String username;
        int score;

        public GameScore(String username, int score) {
            this.username = username;
            this.score = score;
        }

        @Override
        public int compareTo(GameScore other) {
            return Integer.compare(other.score, this.score);
        }
    }

    public void drawScore() {
        path = Paths.get("tetris_score.txt");
        loadedScores = new ArrayList<GameScore>();
        if (Files.exists(path)) {
            try {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String[] tokens = line.split(":");
                    if (tokens.length == 2) {
                        GameScore gamescore = new GameScore(tokens[0].trim(), Integer.parseInt(tokens[1].trim()));
                        loadedScores.add(gamescore);
                    }
                }
                loadedScores.sort(null);
                totalusernum = loadedScores.size();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        } else {
            totalusernum = 0;
        }
        StringBuffer sb = new StringBuffer();
        for (int j = 0; j < Math.min(totalusernum, 10); j++) {
            sb.append(loadedScores.get(j).username + ": " + loadedScores.get(j).score + "\n");
        }
        for (int k = 0; k < 10 - Math.min(totalusernum, 10); k++) {
            sb.append("###: 0\n");
        }
        scorepanel.setText(sb.toString());
        StyledDocument doc = scorepanel.getStyledDocument();
        SimpleAttributeSet styleSet = new SimpleAttributeSet();
		StyleConstants.setFontSize(styleSet, 25);
		StyleConstants.setFontFamily(styleSet, "Monospaced");
        StyleConstants.setBold(styleSet, true);
		StyleConstants.setForeground(styleSet, Color.BLACK);
		StyleConstants.setLineSpacing(styleSet, -0.1f);
        doc.setCharacterAttributes(0, doc.getLength(), styleSet, false);
        scorepanel.setStyledDocument(doc);
    }

    public void resetScore() {
        path = Paths.get("tetris_score.txt");
        try {
            Files.write(path, new ArrayList<String>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class PlayerKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			switch(e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    selectLeft();
                    break;
                case KeyEvent.VK_RIGHT:
                    selectRight();
                    break;
                case KeyEvent.VK_ENTER:
                    selectEnter();
                    break;
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	}

    protected void selectLeft() {
        selectlist.get(selected).setOpaque(false);
        if (selected <= 0) {
            selected = 1;
        } else {
            selected--;
        }
        selectlist.get(selected).setOpaque(true);
        this.revalidate();
        this.repaint();
        return;
    }

    protected void selectRight() {
        selectlist.get(selected).setOpaque(false);
        if (selected >= 1) {
            selected = 0;
        } else {
            selected++;
        }
        selectlist.get(selected).setOpaque(true);
        this.revalidate();
        this.repaint();
        return;
    }

    protected void selectEnter() {
        switch (selected) {
            case 0:
                resetScore();
                drawScore();
                break;
            case 1:
                this.maincontainer.exitScoreboardEnterStart();
                break;
        }
        return;
    }
}