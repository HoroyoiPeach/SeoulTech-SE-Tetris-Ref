package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

public class Settingpanel extends JPanel{

    private Maincontainer maincontainer;
    private JPanel settingPanel;
    private JPanel buttonPanel;
    private JPanel colorBlindPanel;
    private JLabel exitButton;
    private ArrayList<JPanel> selectList;
    private PlayerKeyListener playerKeyListener;
    private int selected = 1;
    private JLabel colorBlindOption;

    public Settingpanel(Maincontainer maincontainer) {
        this.maincontainer = maincontainer;
        setLayout(null);
        setBackground(changeColor(Color.LIGHT_GRAY));
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        settingPanel = new JPanel();
        settingPanel.setLayout(new GridBagLayout());
        settingPanel.setBounds(10, 10, 480, 480);
        settingPanel.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
        settingPanel.setOpaque(false);

            colorBlindPanel = new JPanel();
            colorBlindPanel.setLayout(new GridLayout(1, 0));
            colorBlindPanel.setBackground(changeColor(Color.GRAY));
            colorBlindPanel.setOpaque(false);
            colorBlindPanel.setPreferredSize(new Dimension(400, 50));

                JLabel cbpText = new JLabel("Color Option: ", SwingConstants.CENTER);
                cbpText.setFont(new Font("Courier", Font.BOLD, 16));
                cbpText.setForeground(changeColor(Color.BLACK));
                colorBlindOption = new JLabel("", SwingConstants.CENTER);
                decodeColorSetting(Maincontainer.getColorPalette());
                colorBlindOption.setFont(new Font("Courier", Font.BOLD, 16));
                colorBlindOption.setForeground(changeColor(Color.BLACK));

            colorBlindPanel.add(cbpText);
            colorBlindPanel.add(colorBlindOption);
            
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE;
        settingPanel.add(colorBlindPanel, gbc);

        buttonPanel = new JPanel();
        buttonPanel.setBounds(100, 535, 300, 30);
        buttonPanel.setLayout(new GridLayout(1, 1, 0, 0));
        buttonPanel.setBackground(changeColor(Color.GRAY));
        buttonPanel.setOpaque(true);

            exitButton = new JLabel("EXIT", SwingConstants.CENTER);
            exitButton.setFont(new Font("Courier", Font.BOLD, 20));
            exitButton.setForeground(changeColor(Color.BLACK));
            exitButton.setOpaque(false);

        buttonPanel.add(exitButton);
        selectList = new ArrayList<>(java.util.List.of(colorBlindPanel, buttonPanel));

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
            selected = selectList.size();
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
        if (selected >= selectList.size()) {
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
                changeColorBlindOption();
                break;
            case 1:
                maincontainer.exitSettingEnterStart();
                break;
        }
        return;
    }

    private void changeColorBlindOption() {
        int i = Maincontainer.getColorPalette();
        switch (i) {
            case 0:
                Maincontainer.setColorPalette(1);
                decodeColorSetting(1);
                return;
            case 1:
                Maincontainer.setColorPalette(2);
                decodeColorSetting(2);
                return;
            case 2:
                Maincontainer.setColorPalette(0);
                decodeColorSetting(0);
                return;
        }
    }

    private void decodeColorSetting(int i) {
        switch (i) {
            case 0:
                colorBlindOption.setText("Normal");
                break;
            case 1:
                colorBlindOption.setText("Protanopia");
                break;
            case 2:
                colorBlindOption.setText("Deuteranopia");
                break;
        }
    }
}
