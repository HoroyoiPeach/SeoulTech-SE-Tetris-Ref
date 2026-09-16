package seoultech.se.tetris.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Startpanel extends JPanel{
    public Startpanel(Maincontainer maincontainer) {
		setLayout(new GridBagLayout()); // 컴포넌트 간의 배치를 자유롭게 하기 위해서 GridBagLayout을 사용
		setBackground(Color.LIGHT_GRAY); // background color로 light gray
		setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // padding 값 조절, 필요 시 제거 가능

		// Title 컴포넌트 만들기
        JLabel titlelabel = new JLabel("TETRIS", SwingConstants.CENTER);
        titlelabel.setFont(new Font("Courier", Font.BOLD, 56));
        titlelabel.setForeground(Color.WHITE);

        // Title 컴포넌트 위치를 위한 제약사항
        GridBagConstraints titlegbc = new GridBagConstraints();
        titlegbc.gridx = 0; titlegbc.gridy = 0; // grid[0][0]에 위치
        titlegbc.fill = GridBagConstraints.NONE;
        titlegbc.weightx = 0.0; titlegbc.weighty = 0.3; // 수직 화면의 30% 차지하게 조정

        // Button 컴포넌트 만들기
        JPanel buttonpanel = new JPanel();
        buttonpanel.setLayout(new GridLayout(3, 1, 0, 15)); // 3행 1열, 버튼 사이 간격 15px
        buttonpanel.setOpaque(false); // 투명 배경
        buttonpanel.setPreferredSize(new Dimension(100, 100)); // 컴포넌트 사이즈 지정

        // Button 3개 추가하기
        JButton startbutton = new JButton("START");
        JButton menubutton = new JButton("MENU");
        JButton exitbutton = new JButton("EXIT");
        buttonpanel.add(startbutton);
        buttonpanel.add(menubutton);
        buttonpanel.add(exitbutton);

        // Button 컴포넌트 위치를 위한 제약사항
        GridBagConstraints buttongbc = new GridBagConstraints();
        buttongbc.gridx = 0; buttongbc.gridy = 1; // grid[0][1]에 위치
        buttongbc.fill = GridBagConstraints.NONE;
        buttongbc.weightx = 0.0; buttongbc.weighty = 0.7; // 수직 화면의 70% 차지하게 조정

        // 메인 패널에 컴포넌트 배치
        add(titlelabel, titlegbc);  // 타이틀은 위쪽에
        add(buttonpanel, buttongbc); // 버튼들은 중앙에

        // Button 이벤트 리스너 추가
        startbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                maincontainer.exitStartEnterGame();
            }
        });
        menubutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: exitStartEnterMenu() 생성하기
            }
        });
        exitbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);  // 프로그램 종료
            }
        });
    }
}
