package seoultech.se.tetris.component;

import javax.swing.*;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

public class Startpanel extends JPanel{
    private int selected = 0;
    private KeyListener playerKeyListener;
    private Maincontainer maincontainer;
    private ArrayList<JLabel> selectlist;

    public Startpanel(Maincontainer maincontainer) {
        this.maincontainer = maincontainer;
		setLayout(new GridBagLayout()); // 컴포넌트 간의 배치를 자유롭게 하기 위해서 GridBagLayout을 사용
		setBackground(changeColor(Color.LIGHT_GRAY)); // background color로 light gray
		setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // padding 값 조절, 필요 시 제거 가능

		// Title 컴포넌트 만들기
        JLabel titlelabel = new JLabel("TETRIS", SwingConstants.CENTER);
        titlelabel.setFont(new Font("Courier", Font.BOLD, 56));
        titlelabel.setForeground(changeColor(Color.WHITE));

        // Title 컴포넌트 위치를 위한 제약사항
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; // grid[0][0]에 위치
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0; gbc.weighty = 0.3; // 수직 화면의 30% 차지하게 조정

        add(titlelabel, gbc);  // 타이틀은 위쪽에

        // Button 컴포넌트 만들기
        JPanel buttonpanel = new JPanel();
        buttonpanel.setLayout(new GridLayout(4, 1, 0, 15)); // 3행 1열, 버튼 사이 간격 15px
        buttonpanel.setOpaque(false); // 투명 배경
        buttonpanel.setPreferredSize(new Dimension(100, 150)); // 컴포넌트 사이즈 지정

        // Button 4개 추가하기
        JLabel startbutton = new JLabel("START", SwingConstants.CENTER);
        startbutton.setFont(new Font("Courier", Font.BOLD, 20));
        startbutton.setForeground(changeColor(Color.BLACK));
        startbutton.setBackground(changeColor(Color.GRAY));
        startbutton.setOpaque(true);
        JLabel menubutton = new JLabel("MENU", SwingConstants.CENTER);
        menubutton.setFont(new Font("Courier", Font.BOLD, 20));
        menubutton.setForeground(changeColor(Color.BLACK));
        menubutton.setBackground(changeColor(Color.GRAY));
        JLabel scoreboardbutton = new JLabel("SCORE", SwingConstants.CENTER);
        scoreboardbutton.setFont(new Font("Courier", Font.BOLD, 20));
        scoreboardbutton.setForeground(changeColor(Color.BLACK));
        scoreboardbutton.setBackground(changeColor(Color.GRAY));
        JLabel exitbutton = new JLabel("EXIT", SwingConstants.CENTER);
        exitbutton.setFont(new Font("Courier", Font.BOLD, 20));
        exitbutton.setForeground(changeColor(Color.BLACK));
        exitbutton.setBackground(changeColor(Color.GRAY));
        buttonpanel.add(startbutton);
        buttonpanel.add(menubutton);
        buttonpanel.add(scoreboardbutton);
        buttonpanel.add(exitbutton);
        selectlist = new ArrayList<>(List.of(startbutton, menubutton, scoreboardbutton, exitbutton));

        // Button 컴포넌트 위치를 위한 제약사항
        gbc.gridx = 0; gbc.gridy = 1; // grid[0][1]에 위치
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0; gbc.weighty = 0.7; // 수직 화면의 70% 차지하게 조정

        add(buttonpanel, gbc); // 버튼들은 중앙에

        playerKeyListener = new PlayerKeyListener();
		addKeyListener(playerKeyListener);
		setFocusable(true);
		requestFocus();
    }
    
    private class PlayerKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			switch(e.getKeyCode()) {
			case KeyEvent.VK_DOWN:
                selectDown();
                break;
			case KeyEvent.VK_UP:
                selectUp();
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
    private void selectUp() {
        selectlist.get(selected).setOpaque(false);
        if (selected <= 0) {
            selected = selectlist.size() - 1;
        } else {
            selected--;
        }
        selectlist.get(selected).setOpaque(true);
        this.revalidate();
        this.repaint();
        return;
    }
    private void selectDown() {
        selectlist.get(selected).setOpaque(false);
        if (selected >= selectlist.size() - 1) {
            selected = 0;
        } else {
            selected++;
        }
        selectlist.get(selected).setOpaque(true);
        this.revalidate();
        this.repaint();
        return;
    }
    private void selectEnter() {
        switch (selected) {
            case 0:
                this.maincontainer.exitStartEnterGame();
                break;
            case 1:
                this.maincontainer.exitStartEnterSetting();
                break;
            case 2:
                this.maincontainer.exitStartEnterScoreboard();
                break;
            case 3:
                System.exit(0);
                break;
        }
        return;
    }
}
