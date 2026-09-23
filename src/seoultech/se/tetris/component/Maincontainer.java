package seoultech.se.tetris.component;

import javax.swing.*;
import java.awt.*;

// 창을 구성하는 클래스입니다.
public class Maincontainer extends JFrame{
    private int HEIGHT = 600; // container height size
    private int WIDTH = 500; // container width size
    private static int colorPalette = 0; // 0: 일반 색상, 1: 적색약, 2: 녹색약 (청색약은 희귀성으로 인해 미추가)
    private JPanel mainpanel; // container에 띄울 panel로 background color가 black인 아무것도 없는 panel입니다.
    private CardLayout cardlayout; // 추후 mainpanel에 추가될 panel끼리 전환하기 위한 기반입니다. 현재는 코드에 큰 영향이 없습니다.
    private Startpanel startpanel; // 시작 시 보여질 화면 panel입니다.
    private Gamepanel gamepanel; // 게임 화면 panel입니다.
    private Settingpanel settingpanel;
    private Scoreboardpanel scoreboardpanel;

    public Maincontainer() {
        setTitle("SeoulTech SE Tetris"); // 프로그램 이름 설정
        setSize(WIDTH, HEIGHT); // 띄워질 터미널 창 크기 지정
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 터미널 닫기 설정
        setResizable(false); // 터미널 창 크기를 바꾸지 못하게 고정
        
        cardlayout = new CardLayout();
        mainpanel = new JPanel(cardlayout); // container에 띄우기 위한 빈 panel
        mainpanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        mainpanel.setBackground(Color.BLACK);
        add(mainpanel, BorderLayout.CENTER); // container에 mainpanel 추가
        pack();
        setVisible(true);
    }

    public void enterStart() { // 게임이 시작됨을 알리는 메소드
        startpanel = new Startpanel(this); // Startpanel 객체 생성 및 container 객체 넘겨주기
        mainpanel.add(startpanel, "StartScreen"); // mainpanel에 startpanel 추가
        mainpanel.revalidate(); // 변경사항을 재계산
        mainpanel.repaint(); // 변경사항 화면에 다시 그리기
        cardlayout.show(mainpanel, "StartScreen");
        startpanel.requestFocusInWindow();
    }
    // Start 버튼 상호작용 함수
    protected void exitStartEnterGame() { // 시작화면에서 시작 버튼을 눌러 게임화면으로 들어감을 알리는 메소드
        gamepanel = new Gamepanel(this); // Gamepanel 객체 생성 및 container 객체 넘겨주기
        mainpanel.add(gamepanel, "GameScreen"); // mainpanel에 gamepanel 추가
        mainpanel.remove(startpanel); // 게임 시작 시에는 시작 화면이 불필요하므로, startpanel 제거하여 memory leaking 방지하기
        mainpanel.revalidate(); // 변경사항을 재계산
        mainpanel.repaint(); // 변경사항 화면에 다시 그리기
        cardlayout.show(mainpanel, "GameScreen");
        gamepanel.requestFocusInWindow();
    }
    protected void exitGameEnterStart() {
        startpanel = new Startpanel(this);
        mainpanel.add(startpanel, "StartScreen");
        if (gamepanel != null && gamepanel.getParent() != null) mainpanel.remove(gamepanel);
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "StartScreen");
        startpanel.requestFocusInWindow();
    }
    // Setting 버튼 상호작용 함수
    protected void exitStartEnterSetting() {
        settingpanel = new Settingpanel(this);
        mainpanel.add(settingpanel, "MenuScreen");
        mainpanel.remove(startpanel);
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "MenuScreen");
        settingpanel.requestFocusInWindow();
    }
    protected void exitSettingEnterStart() {
        startpanel = new Startpanel(this);
        mainpanel.add(startpanel, "StartScreen");
        mainpanel.remove(settingpanel);
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "StartScreen");
        startpanel.requestFocusInWindow();
    }
    // Scoreboard 버튼 상호작용 함수
    protected void exitStartEnterScoreboard() {
        scoreboardpanel = new Scoreboardpanel(this);
        mainpanel.add(scoreboardpanel, "ScoreboardScreen");
        mainpanel.remove(startpanel);
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "ScoreboardScreen");
        scoreboardpanel.requestFocusInWindow();
    }
    protected void exitScoreboardEnterStart() {
        startpanel = new Startpanel(this);
        mainpanel.add(startpanel, "StartScreen");
        mainpanel.remove(scoreboardpanel);
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "StartScreen");
        startpanel.requestFocusInWindow();
    }

    public static Color changeColor(Color exColor) {
        Color newColor = exColor;
        int r = exColor.getRed(); int g = exColor.getGreen();
        int b = exColor.getBlue(); int a = exColor.getAlpha();
        int newR; int newG; int newB; int newA;
        switch (colorPalette) {
            case 0:
                return newColor;
            case 1:
                newR = r;
                newG = (int) (0.5089 * r + 0.4911 * g);
                newB = (int) (0.6174 * r - 0.6173 * g + b);
                newA = a;
                newR = Math.max(0, Math.min(255, newR));
                newG = Math.max(0, Math.min(255, newG));
                newB = Math.max(0, Math.min(255, newB));
                newColor = new Color(newR, newG, newB, newA);
                return newColor;  
            case 2:
                newR = r;
                newG = (int) (0.2023 * r + 0.7977 * g);
                newB = (int) (0.5174 * r - 0.5175 * g + b);
                newA = a;
                newR = Math.max(0, Math.min(255, newR));
                newG = Math.max(0, Math.min(255, newG));
                newB = Math.max(0, Math.min(255, newB));
                newColor = new Color(newR, newG, newB, newA);
                return newColor;
            default:
                return newColor;
        }
    }

    public static void setColorPalette(int i) {
        colorPalette = i;
    }

    public static int getColorPalette() {
        return colorPalette;
    }
}
