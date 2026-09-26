package seoultech.se.tetris.component;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

// 창을 구성하는 클래스입니다.
public class Maincontainer extends JFrame{
    private static int HEIGHT = 600;
    private static int WIDTH = 500;
    protected static int ROTATE = 38;
    protected static int DOWN = 40;
    protected static int LEFT = 37;
    protected static int RIGHT = 39;
    protected static int PAUSE = 80;
    protected static String colorPalette = "Normal"; // 0: 일반 색상, 1: 적색약, 2: 녹색약 (청색약은 희귀성으로 인해 미추가)
    protected Setting setting;
    private JPanel mainPanel; // container에 띄울 panel로 background color가 black인 아무것도 없는 panel입니다.
    private CardLayout cardLayout; // 추후 mainPanel에 추가될 panel끼리 전환하기 위한 기반입니다. 현재는 코드에 큰 영향이 없습니다.
    private Startpanel startPanel; // 시작 시 보여질 화면 panel입니다.
    private Gamepanel gamePanel; // 게임 화면 panel입니다.
    private Settingpanel settingPanel;
    private Scoreboardpanel scoreboardPanel;

    public Maincontainer() {
        setting = new Setting();
        applySetting();
    }

    public void enterStart() { // 게임이 시작됨을 알리는 메소드
        startPanel = new Startpanel(this); // Startpanel 객체 생성 및 container 객체 넘겨주기
        mainPanel.add(startPanel, "StartScreen"); // mainPanel에 startPanel 추가
        mainPanel.revalidate(); // 변경사항을 재계산
        mainPanel.repaint(); // 변경사항 화면에 다시 그리기
        cardLayout.show(mainPanel, "StartScreen");
        startPanel.requestFocusInWindow();
    }
    // Start 버튼 상호작용 함수
    protected void exitStartEnterGame() { // 시작화면에서 시작 버튼을 눌러 게임화면으로 들어감을 알리는 메소드
        gamePanel = new Gamepanel(this); // Gamepanel 객체 생성 및 container 객체 넘겨주기
        mainPanel.add(gamePanel, "GameScreen"); // mainPanel에 gamePanel 추가
        mainPanel.remove(startPanel); // 게임 시작 시에는 시작 화면이 불필요하므로, startPanel 제거하여 memory leaking 방지하기
        mainPanel.revalidate(); // 변경사항을 재계산
        mainPanel.repaint(); // 변경사항 화면에 다시 그리기
        cardLayout.show(mainPanel, "GameScreen");
        gamePanel.requestFocusInWindow();
    }
    protected void exitGameEnterStart() {
        startPanel = new Startpanel(this);
        mainPanel.add(startPanel, "StartScreen");
        if (gamePanel != null && gamePanel.getParent() != null) mainPanel.remove(gamePanel);
        mainPanel.revalidate();
        mainPanel.repaint();
        cardLayout.show(mainPanel, "StartScreen");
        startPanel.requestFocusInWindow();
    }
    // Setting 버튼 상호작용 함수
    protected void exitStartEnterSetting() {
        settingPanel = new Settingpanel(this);
        mainPanel.add(settingPanel, "MenuScreen");
        mainPanel.remove(startPanel);
        mainPanel.revalidate();
        mainPanel.repaint();
        cardLayout.show(mainPanel, "MenuScreen");
        settingPanel.requestFocusInWindow();
    }
    protected void exitSettingEnterStart() {
        startPanel = new Startpanel(this);
        remove(mainPanel);
        applySetting();
        mainPanel.add(startPanel, "StartScreen");
        mainPanel.remove(settingPanel);
        mainPanel.revalidate();
        mainPanel.repaint();
        cardLayout.show(mainPanel, "StartScreen");
        startPanel.requestFocusInWindow();
    }
    // Scoreboard 버튼 상호작용 함수
    protected void exitStartEnterScoreboard() {
        scoreboardPanel = new Scoreboardpanel(this);
        mainPanel.add(scoreboardPanel, "ScoreboardScreen");
        mainPanel.remove(startPanel);
        mainPanel.revalidate();
        mainPanel.repaint();
        cardLayout.show(mainPanel, "ScoreboardScreen");
        scoreboardPanel.requestFocusInWindow();
    }
    protected void exitScoreboardEnterStart() {
        startPanel = new Startpanel(this);
        mainPanel.add(startPanel, "StartScreen");
        mainPanel.remove(scoreboardPanel);
        mainPanel.revalidate();
        mainPanel.repaint();
        cardLayout.show(mainPanel, "StartScreen");
        startPanel.requestFocusInWindow();
    }

    private void applySetting() {
        Integer[] h_w = setting.getWindowSize();
        HEIGHT = h_w[0]; WIDTH = h_w[1];
        Integer[] keys = setting.getKeySetting();
        ROTATE = keys[0]; DOWN = keys[1]; LEFT = keys[2]; RIGHT = keys[3]; PAUSE = keys[4];
        colorPalette = setting.getColorBlind();

        setTitle("SeoulTech SE Tetris"); // 프로그램 이름 설정
        setSize(WIDTH, HEIGHT); // 띄워질 터미널 창 크기 지정
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 터미널 닫기 설정
        setResizable(false); // 터미널 창 크기를 바꾸지 못하게 고정
        
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout); // container에 띄우기 위한 빈 panel
        mainPanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        mainPanel.setBackground(Color.BLACK);
        add(mainPanel, BorderLayout.CENTER); // container에 mainPanel 추가
        pack();
        setVisible(true);
    }

    public static Color changeColor(Color exColor) {
        Color newColor = exColor;
        int r = exColor.getRed(); int g = exColor.getGreen();
        int b = exColor.getBlue(); int a = exColor.getAlpha();
        int newR; int newG; int newB; int newA;
        switch (colorPalette) {
            case "Normal":
                return newColor;
            case "Protanopia":
                newR = r;
                newG = (int) (0.5089 * r + 0.4911 * g);
                newB = (int) (0.6174 * r - 0.6173 * g + b);
                newA = a;
                newR = Math.max(0, Math.min(255, newR));
                newG = Math.max(0, Math.min(255, newG));
                newB = Math.max(0, Math.min(255, newB));
                newColor = new Color(newR, newG, newB, newA);
                return newColor;  
            case "Deuteranopia":
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

    public class Setting {

        private Map<String, String> settingMap;
        private final ArrayList<String> settingList = new ArrayList<>(List.of("windowSize","keySetting","resetScore","colorBlind")); //ISSUE: resetScore의 값이 true가 되는 일은 없을 것 같아서 없애도 될 것 같아요

        public Setting() {
            settingMap = new HashMap<>();
            clearSetting();
            decodeSetting();
        }

        private void decodeSetting() {
            Path path = Paths.get("setting.txt");
            if (Files.exists(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        if (line.trim().isEmpty()) continue;
                        String[] tokens = line.split(":");
                        if ((tokens.length == 2) && (settingList.contains(tokens[0]))) {
                            settingMap.put(tokens[0],tokens[1]);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        protected void setWindowSize(String str) {
            settingMap.put("windowSize", str);
        }

        protected Integer[] getWindowSize() {
            String[] parts = settingMap.get("windowSize").split("X");
            Integer[] h_w = new Integer[2];
            h_w[0] = Integer.parseInt(parts[0]);
            h_w[1] = Integer.parseInt(parts[1]);
            return h_w;
        }

        protected void setKeySetting(String str) {
            settingMap.put("keySetting", str);
        }

        protected Integer[] getKeySetting() {
            String[] parts = settingMap.get("keySetting").split(",");
            Integer[] keys = new Integer[5];
            for (int i = 0; i < 5; i++) keys[i] = Integer.parseInt(parts[i].split("=")[1]);
            return keys;
        }

        protected void setColorBlind(String str) {
            settingMap.put("colorBlind", str);
        }

        protected String getColorBlind() {
            return settingMap.get("colorBlind");
        }

        protected void clearSetting() {
            settingMap.put("windowSize", "600X500");
            settingMap.put("keySetting", "ROTATE=38,DOWN=40,LEFT=37,RIGHT=39,PAUSE=80");
            settingMap.put("resetScore", "false");
            settingMap.put("colorBlind","Normal");
        }

        protected void exportSetting() {
            Path path = Paths.get("setting.txt");
            StringBuffer sb = new StringBuffer();
            for (String line : settingList) {
                String str = line + ":" + settingMap.get(line) + "\n";
                sb.append(str);
            }    
            try {
                Files.write(path, Arrays.asList(sb.toString()), 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }        
        }
    }
}
