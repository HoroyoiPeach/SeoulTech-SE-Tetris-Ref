package seoultech.se.tetris.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Startpanel extends JPanel{
    public Startpanel(Maincontainer maincontainer) {
		setLayout(new GridBagLayout()); // set margin of component
		setBackground(Color.LIGHT_GRAY); // set background color light gray
		setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // set padding of panel

		// 3. Make title label
        JLabel titlelabel = new JLabel("TETRIS", SwingConstants.CENTER);
        titlelabel.setFont(new Font("Courier", Font.BOLD, 56));
        titlelabel.setForeground(Color.WHITE);

        GridBagConstraints titlegbc = new GridBagConstraints();
        titlegbc.gridx = 0; titlegbc.gridy = 0;
        titlegbc.fill = GridBagConstraints.NONE;
        titlegbc.weightx = 0.0; titlegbc.weighty = 0.3;

        // 4. Make buttons panel
        JPanel buttonpanel = new JPanel();
        buttonpanel.setLayout(new GridLayout(3, 1, 0, 15)); // 2행 1열 구조, 버튼 사이 간격 15px
        buttonpanel.setOpaque(false); // 배경을 투명하게 해서 메인 패널 색상이 보이도록 설정
        buttonpanel.setPreferredSize(new Dimension(100, 100));

        JButton startbutton = new JButton("START");
        JButton menubutton = new JButton("MENU");
        JButton exitbutton = new JButton("EXIT");
        startbutton.setPreferredSize(new Dimension(20, 10));
        menubutton.setPreferredSize(new Dimension(20, 10));
        exitbutton.setPreferredSize(new Dimension(20, 10));
        buttonpanel.add(startbutton);
        buttonpanel.add(menubutton);
        buttonpanel.add(exitbutton);

        GridBagConstraints buttongbc = new GridBagConstraints();
        buttongbc.gridx = 0; buttongbc.gridy = 1;
        buttongbc.fill = GridBagConstraints.NONE;
        buttongbc.weightx = 0.0; buttongbc.weighty = 0.7;

        // 5. 메인 패널에 컴포넌트 배치
        add(titlelabel, titlegbc);  // 타이틀은 위쪽에
        add(buttonpanel, buttongbc); // 버튼들은 중앙에

        // 6. 버튼 이벤트 리스너 추가
        startbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                maincontainer.exitStartEnterGame();
            }
        });

        menubutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: menu 창을 생성
            }
        });

        exitbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 프로그램 즉시 종료
                System.exit(0); 
            }
        });
    }
}
