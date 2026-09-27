package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Settingpanel extends JPanel{

    private Maincontainer maincontainer;
    private JPanel settingPanel;
    private JPanel buttonPanel;
    private JPanel windowSizePanel;
    private JPanel keySettingPanel;
    private JPanel resetScorePanel;
    private JPanel colorBlindPanel;
    private JPanel resetSettingPanel;
    private JLabel exitButton;
    private ArrayList<JPanel> selectList;
    private PlayerKeyListener playerKeyListener;
    private int selected = 0;
    private JLabel windowSizeOption;
    private JLabel colorBlindOption;
    private Integer[] h_w;
    private int HEIGHT;
    private int WIDTH;

    // TODO: 세팅 추가 시 지침: 1. JPanel 생성 후 settingPanel에 등록 2. selectList에 해당 Panel 등록 3. selectEnter 메소드에 기능 구현
    public Settingpanel(Maincontainer maincontainer) {
        this.maincontainer = maincontainer;
        setLayout(null);
        setBackground(changeColor(Color.LIGHT_GRAY));

        h_w = maincontainer.setting.getWindowSize();
        HEIGHT = h_w[0]; WIDTH = h_w[1];
        int panelWidth = 0; int panelHeight = 0;
        Dimension labelSize;

        settingPanel = new JPanel();
        settingPanel.setLayout(new GridBagLayout());
        settingPanel.setBounds(10, 10, WIDTH-20, 3*(HEIGHT-20)/4);
        settingPanel.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
        settingPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
            // 테트리스 게임 화면의 크기를 조절 - 최소 3가지 이상 미리 정의된 크기.
            windowSizePanel = new JPanel();
            windowSizePanel.setLayout(new GridLayout(1, 0));
            windowSizePanel.setBackground(changeColor(Color.GRAY));
            windowSizePanel.setOpaque(true);

                JLabel wspText = new JLabel("Window Option: ", SwingConstants.CENTER);
                wspText.setFont(new Font("Courier", Font.BOLD, 16));
                wspText.setForeground(changeColor(Color.BLACK));
                String str = Arrays.stream(maincontainer.setting.getWindowSize()).map(String::valueOf).collect(Collectors.joining("X"));
                windowSizeOption = new JLabel(str, SwingConstants.CENTER);
                windowSizeOption.setFont(new Font("Courier", Font.BOLD, 16));
                windowSizeOption.setForeground(changeColor(Color.BLACK));
            
            labelSize = wspText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            windowSizePanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            windowSizePanel.add(wspText);
            windowSizePanel.add(windowSizeOption);
            gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE;
            settingPanel.add(windowSizePanel, gbc);
            // 게임 조작을 위해 사용될 키를 설정
            keySettingPanel = new JPanel();
            keySettingPanel.setLayout(new GridLayout(1, 0));
            keySettingPanel.setBackground(changeColor(Color.GRAY));
            keySettingPanel.setOpaque(false);

                JLabel kspText = new JLabel("Change Keys", SwingConstants.CENTER);
                kspText.setFont(new Font("Courier", Font.BOLD, 16));
                kspText.setForeground(changeColor(Color.BLACK));

            labelSize = kspText.getPreferredSize();
            panelWidth = labelSize.width + 20;
            panelHeight = labelSize.height + 20;
            keySettingPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            keySettingPanel.add(kspText);
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
            settingPanel.add(keySettingPanel, gbc);
            // 스코어 보드의 기록을 초기화
            resetScorePanel = new JPanel();
            resetScorePanel.setLayout(new GridLayout(1, 0));
            resetScorePanel.setBackground(changeColor(Color.GRAY));
            resetScorePanel.setOpaque(false);
            resetScorePanel.setPreferredSize(new Dimension(200, 50));

                JLabel rsText = new JLabel("Reset Scoreboard", SwingConstants.CENTER);
                rsText.setFont(new Font("Courier", Font.BOLD, 16));
                rsText.setForeground(changeColor(Color.BLACK));

            labelSize = rsText.getPreferredSize();
            panelWidth = labelSize.width + 20;
            panelHeight = labelSize.height + 20;
            resetScorePanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            resetScorePanel.add(rsText);
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
            settingPanel.add(resetScorePanel, gbc);
            // 색맹 모드 켜고 끄기.
            colorBlindPanel = new JPanel();
            colorBlindPanel.setLayout(new GridLayout(1, 0));
            colorBlindPanel.setBackground(changeColor(Color.GRAY));
            colorBlindPanel.setOpaque(false);
            colorBlindPanel.setPreferredSize(new Dimension(400, 50));

                JLabel cbpText = new JLabel("Color Option: ", SwingConstants.CENTER);
                cbpText.setFont(new Font("Courier", Font.BOLD, 16));
                cbpText.setForeground(changeColor(Color.BLACK));
                colorBlindOption = new JLabel(maincontainer.setting.getColorBlind(), SwingConstants.CENTER);
                colorBlindOption.setFont(new Font("Courier", Font.BOLD, 16));
                colorBlindOption.setForeground(changeColor(Color.BLACK));

            labelSize = cbpText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            colorBlindPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            colorBlindPanel.add(cbpText);
            colorBlindPanel.add(colorBlindOption);
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
            settingPanel.add(colorBlindPanel, gbc);   
            //모든 설정으로 기본 설정으로 되돌리기.
            resetSettingPanel = new JPanel();
            resetSettingPanel.setLayout(new GridLayout(1, 0));
            resetSettingPanel.setBackground(changeColor(Color.GRAY));
            resetSettingPanel.setOpaque(false);
            resetSettingPanel.setPreferredSize(new Dimension(230, 50));

                JLabel rspText = new JLabel("Reset setting", SwingConstants.CENTER);
                rspText.setFont(new Font("Courier", Font.BOLD, 16));
                rspText.setForeground(changeColor(Color.BLACK));

            labelSize = rspText.getPreferredSize();
            panelWidth = labelSize.width + 20;
            panelHeight = labelSize.height + 20;
            resetSettingPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            resetSettingPanel.add(rspText);
            gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
            settingPanel.add(resetSettingPanel, gbc);

        buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 1, 0, 0));
        buttonPanel.setBackground(changeColor(Color.GRAY));
        buttonPanel.setOpaque(false);

            exitButton = new JLabel("Save and Exit", SwingConstants.CENTER);
            exitButton.setFont(new Font("Courier", Font.BOLD, 16));
            exitButton.setForeground(changeColor(Color.BLACK));
            exitButton.setOpaque(false);
        
        labelSize = exitButton.getPreferredSize();
        panelWidth = labelSize.width + 20;
        panelHeight = labelSize.height + 20;
        buttonPanel.setBounds((WIDTH-panelWidth)/2, (3*(HEIGHT-20)/4+10)+((HEIGHT-10)-(3*(HEIGHT-20)/4+10)-panelHeight)/2, panelWidth, panelHeight);
        buttonPanel.add(exitButton);
        selectList = new ArrayList<>(java.util.List.of(windowSizePanel, keySettingPanel, resetScorePanel, colorBlindPanel, resetSettingPanel, buttonPanel));

        add(settingPanel);
        add(buttonPanel);

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
        selectList.get(selected).setOpaque(false);
        if (selected <= 0) {
            selected = selectList.size() - 1;
        } else {
            selected--;
        }
        selectList.get(selected).setOpaque(true);
        this.revalidate();
        this.repaint();
        return;
    }
    private void selectDown() {
        selectList.get(selected).setOpaque(false);
        if (selected >= selectList.size() - 1) {
            selected = 0;
        } else {
            selected++;
        }
        selectList.get(selected).setOpaque(true);
        this.revalidate();
        this.repaint();
        return;
    }
    private void selectEnter() {
        switch (selected) {
            case 0:
                changeWindowSizeOption();
                break;
            case 1:
                changeKeySettingOption();
                break;
            case 2:
                resetScore();
                break;
            case 3:
                changeColorBlindOption();
                break;
            case 4:
                resetSetting();
                break;
            case 5:
                maincontainer.setting.exportSetting();
                maincontainer.exitSettingEnterStart();
                break;
        }
    }

    private void changeWindowSizeOption() {
        switch (Arrays.stream(maincontainer.setting.getWindowSize()).map(String::valueOf).collect(Collectors.joining("X"))) {
            case "600X500":
                windowSizeOption.setText("750X625");
                maincontainer.setting.setWindowSize("750X625");;
                break;
            case "750X625":
                windowSizeOption.setText("900X750");
                maincontainer.setting.setWindowSize("900X750");
                break;
            case "900X750":
                windowSizeOption.setText("600X500");
                maincontainer.setting.setWindowSize("600X500");
                break;
        }
    }

    private void changeKeySettingOption() {
        removeKeyListener(playerKeyListener);
                ChangeKeyPanel changeKeyPanel = new ChangeKeyPanel(this);
                changeKeyPanel.setBounds(0, 0, this.getWidth(), this.getHeight());
                add(changeKeyPanel);
                setComponentZOrder(changeKeyPanel, 0);
                changeKeyPanel.setFocusable(true);
                revalidate();
                repaint();
                changeKeyPanel.requestFocusInWindow();
    }

    private void resetScore() {
        Path path = Paths.get("tetris_score.txt");
        try {
            Files.write(path, new ArrayList<String>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void changeColorBlindOption() {
        switch (maincontainer.setting.getColorBlind()) {
            case "Normal":
                colorBlindOption.setText("Protanopia");
                maincontainer.setting.setColorBlind("Protanopia");;
                break;
            case "Protanopia":
                colorBlindOption.setText("Deuteranopia");
                maincontainer.setting.setColorBlind("Deuteranopia");
                break;
            case "Deuteranopia":
                colorBlindOption.setText("Normal");
                maincontainer.setting.setColorBlind("Normal");
                break;
        }
    }

    private void resetSetting() {
        maincontainer.setting.clearSetting();
        windowSizeOption.setText(Arrays.stream(maincontainer.setting.getWindowSize()).map(String::valueOf).collect(Collectors.joining("X")));
        colorBlindOption.setText(maincontainer.setting.getColorBlind());
        revalidate();
        repaint();
    }

    private class ChangeKeyPanel extends JPanel {
        private Settingpanel settingpanel;
        private int selected = 0;
        private ArrayList<JPanel> selectList;
        private ArrayList<JLabel> selectOption;
        private JPanel rotatePanel;
        private JPanel downPanel;
        private JPanel leftPanel;
        private JPanel rightPanel;
        private JPanel pausePanel;
        private JPanel exitPanel;
        private JLabel rotateOption;
        private JLabel downOption;
        private JLabel leftOption;
        private JLabel rightOption;
        private JLabel pauseOption;
        private Integer[] currentKey;
        private PlayerKeyListener2 keylistener2;
         
        public ChangeKeyPanel(Settingpanel settingpanel) {
            this.settingpanel = settingpanel;
            setLayout(null);
            setBackground(changeColor(Color.LIGHT_GRAY));
            setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
            JPanel keyList = new JPanel();
            add(keyList);
            keyList.setBounds(10, 10, settingpanel.settingPanel.getWidth(), settingpanel.settingPanel.getHeight());
            keyList.setLayout(new GridBagLayout());
            currentKey = settingpanel.maincontainer.setting.getKeySetting();
            int panelWidth = 0; int panelHeight = 0;
            Dimension labelSize;

            GridBagConstraints gbc = new GridBagConstraints();

            rotatePanel = new JPanel();
            rotatePanel.setLayout(new GridLayout(1, 0));
            rotatePanel.setBackground(changeColor(Color.GRAY));
            rotatePanel.setOpaque(true);

                JLabel rotateText = new JLabel("Rotate: ", SwingConstants.CENTER);
                rotateText.setFont(new Font("Courier", Font.BOLD, 16));
                rotateText.setForeground(changeColor(Color.BLACK));
                rotateOption = new JLabel(KeyEvent.getKeyText(currentKey[0]), SwingConstants.CENTER);
                rotateOption.setFont(new Font("Courier", Font.BOLD, 16));
                rotateOption.setForeground(changeColor(Color.BLACK));

            labelSize = rotateText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            rotatePanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            rotatePanel.add(rotateText);
            rotatePanel.add(rotateOption);
            gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE;
            keyList.add(rotatePanel, gbc);

            downPanel = new JPanel();
            downPanel.setLayout(new GridLayout(1, 0));
            downPanel.setBackground(changeColor(Color.GRAY));
            downPanel.setOpaque(false);

                JLabel downText = new JLabel("Down: ", SwingConstants.CENTER);
                downText.setFont(new Font("Courier", Font.BOLD, 16));
                downText.setForeground(changeColor(Color.BLACK));
                downOption = new JLabel(KeyEvent.getKeyText(currentKey[1]), SwingConstants.CENTER);
                downOption.setFont(new Font("Courier", Font.BOLD, 16));
                downOption.setForeground(changeColor(Color.BLACK));

            labelSize = downText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            downPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            downPanel.add(downText);
            downPanel.add(downOption);
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
            keyList.add(downPanel, gbc);

            leftPanel = new JPanel();
            leftPanel.setLayout(new GridLayout(1, 0));
            leftPanel.setBackground(changeColor(Color.GRAY));
            leftPanel.setOpaque(false);

                JLabel leftText = new JLabel("Left: ", SwingConstants.CENTER);
                leftText.setFont(new Font("Courier", Font.BOLD, 16));
                leftText.setForeground(changeColor(Color.BLACK));
                leftOption = new JLabel(KeyEvent.getKeyText(currentKey[2]), SwingConstants.CENTER);
                leftOption.setFont(new Font("Courier", Font.BOLD, 16));
                leftOption.setForeground(changeColor(Color.BLACK));

            labelSize = leftText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            leftPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            leftPanel.add(leftText);
            leftPanel.add(leftOption);
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
            keyList.add(leftPanel, gbc);

            rightPanel = new JPanel();
            rightPanel.setLayout(new GridLayout(1, 0));
            rightPanel.setBackground(changeColor(Color.GRAY));
            rightPanel.setOpaque(false);

                JLabel rightText = new JLabel("Right: ", SwingConstants.CENTER);
                rightText.setFont(new Font("Courier", Font.BOLD, 16));
                rightText.setForeground(changeColor(Color.BLACK));
                rightOption = new JLabel(KeyEvent.getKeyText(currentKey[3]), SwingConstants.CENTER);
                rightOption.setFont(new Font("Courier", Font.BOLD, 16));
                rightOption.setForeground(changeColor(Color.BLACK));

            labelSize = rightText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            rightPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            rightPanel.add(rightText);
            rightPanel.add(rightOption);
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
            keyList.add(rightPanel, gbc);

            pausePanel = new JPanel();
            pausePanel.setLayout(new GridLayout(1, 0));
            pausePanel.setBackground(changeColor(Color.GRAY));
            pausePanel.setOpaque(false);

                JLabel pauseText = new JLabel("Pause: ", SwingConstants.CENTER);
                pauseText.setFont(new Font("Courier", Font.BOLD, 16));
                pauseText.setForeground(changeColor(Color.BLACK));
                pauseOption = new JLabel(KeyEvent.getKeyText(currentKey[4]), SwingConstants.CENTER);
                pauseOption.setFont(new Font("Courier", Font.BOLD, 16));
                pauseOption.setForeground(changeColor(Color.BLACK));

            labelSize = pauseText.getPreferredSize();
            panelWidth = labelSize.width * 2 + 20;
            panelHeight = labelSize.height + 20;
            pausePanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            pausePanel.add(pauseText);
            pausePanel.add(pauseOption);
            gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
            keyList.add(pausePanel, gbc);

            exitPanel = new JPanel();
            exitPanel.setLayout(new GridLayout(1, 0));
            exitPanel.setBackground(changeColor(Color.GRAY));
            exitPanel.setOpaque(false);

                JLabel exitText = new JLabel("Save and Exit", SwingConstants.CENTER);
                exitText.setFont(new Font("Courier", Font.BOLD, 16));
                exitText.setForeground(changeColor(Color.BLACK));

            labelSize = exitText.getPreferredSize();
            panelWidth = labelSize.width + 20;
            panelHeight = labelSize.height + 20;
            exitPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
            exitPanel.add(exitText);
            gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE;
            keyList.add(exitPanel, gbc);

            selectList = new ArrayList<>(java.util.List.of(rotatePanel, downPanel, leftPanel, rightPanel, pausePanel, exitPanel));
            selectOption = new ArrayList<>(java.util.List.of(rotateOption, downOption, leftOption, rightOption, pauseOption));
            keylistener2 = new PlayerKeyListener2();
            this.addKeyListener(keylistener2);
            setFocusable(true);
            requestFocusInWindow();
        }

        private class PlayerKeyListener2 implements KeyListener {
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
        private void selectDown() {
            selectList.get(selected).setOpaque(false);
            if (selected >= selectList.size() - 1) {
                selected = 0;
            } else {
                selected++;
            }
            selectList.get(selected).setOpaque(true);
            revalidate();
            repaint();
        }
        private void selectUp() {
            selectList.get(selected).setOpaque(false);
            if (selected <= 0) {
                selected = selectList.size() - 1;
            } else {
                selected--;
            }
            selectList.get(selected).setOpaque(true);
            revalidate();
            repaint();
        }
        private void selectEnter() {
            switch (selected) {
                case 0:
                    changeKey(0);
                    break;
                case 1:
                    changeKey(1);
                    break;
                case 2:
                    changeKey(2);
                    break;
                case 3:
                    changeKey(3);
                    break;
                case 4:
                    changeKey(4);
                    break;
                case 5:
                    String str = "ROTATE=" + currentKey[0] + ",DOWN=" + currentKey[1] + ",LEFT=" + currentKey[2] + ",RIGHT=" + currentKey[3] + ",PAUSE=" + currentKey[4];
                    settingpanel.maincontainer.setting.setKeySetting(str);
                    removeKeyListener(keylistener2);
                    settingpanel.addKeyListener(settingpanel.playerKeyListener);
                    settingpanel.remove(this);
                    settingpanel.revalidate();
                    settingpanel.repaint();
                    settingpanel.setFocusable(true);
                    settingpanel.requestFocusInWindow();
                    break;
            }
        }
        private void changeKey(int idx) {
            removeKeyListener(keylistener2);
            JPanel display = new JPanel();
            display.setLayout(new GridLayout());
            this.add(display);
            int w = settingpanel.settingPanel.getWidth();
            int h = settingpanel.settingPanel.getHeight();
            display.setBounds(10+w/4-25, 10+h/4, w/2+50, h/2);
            display.setBackground(changeColor(Color.LIGHT_GRAY));
            JLabel text = new JLabel("Press Key to Change", SwingConstants.CENTER);
            text.setFont(new Font("Courier", Font.BOLD, 16));
            text.setForeground(changeColor(Color.BLACK));
            display.add(text);
            setComponentZOrder(display, 0);
            revalidate();
            repaint();
            KeyListener customKeyListener = new KeyAdapter() {
                @Override 
                public void keyPressed(KeyEvent e) {
                    int newKey = e.getKeyCode();
                    String keyName = KeyEvent.getKeyText(newKey);
                    if (!Arrays.asList(currentKey).contains(newKey) || newKey == currentKey[idx]) {
                        currentKey[idx] = newKey;
                        selectOption.get(idx).setText(keyName);
                        removeKeyListener(this);
                        addKeyListener(keylistener2);
                        Container parent = display.getParent();
                        parent.remove(display);
                        parent.revalidate();
                        parent.repaint();
                        return;
                    } else {
                        text.setText("Please Enter an Unused Key");
                        display.revalidate();
                        display.repaint();
                        javax.swing.Timer delay = new javax.swing.Timer(2000, ie -> {
                            removeKeyListener(this);
                            addKeyListener(keylistener2);
                            Container parent = display.getParent();
                            parent.remove(display);
                            parent.revalidate();
                            parent.repaint();
                        });
                        delay.setRepeats(false);
                        delay.start();
                        return;
                    }
                }
            };
            addKeyListener(customKeyListener);
            requestFocusInWindow();
        }
    }
}
