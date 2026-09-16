package seoultech.se.tetris.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Menupanel extends JPanel{
    public Menupanel(Maincontainer maincontainer) {
        setLayout(new GridBagLayout());
        setBackground(Color.CYAN);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20)); 

        JPanel buttonpanel = new JPanel();
        buttonpanel.setLayout(new GridLayout(1, 1, 0, 15)); // 3행 1열, 버튼 사이 간격 15px
        buttonpanel.setOpaque(false); // 투명 배경

        JButton exitbutton = new JButton("Return to main");
        buttonpanel.add(exitbutton);

        add(buttonpanel);

        exitbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                maincontainer.exitMenuEnterStart();
            }
        });
    }
}
