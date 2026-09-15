package seoultech.se.tetris.component;

import javax.swing.*;
import java.awt.*;
// import java.awt.event.ActionEvent;
// import java.awt.event.ActionListener;

public class Maincontainer extends JFrame{
    private int HEIGHT = 500; // container height size
    private int WIDTH = 400; // container width size
    private JPanel mainpanel;
    private CardLayout cardlayout;
    private Startpanel startpanel;
    private Gamepanel gamepanel;

    public Maincontainer() {
        setTitle("SeoulTech SE Tetris");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(false);
        
        cardlayout = new CardLayout();
        mainpanel = new JPanel(cardlayout);
        mainpanel.setBackground(Color.BLACK);
        add(mainpanel, BorderLayout.CENTER);
    }

    public void enterStart() {
        startpanel = new Startpanel(this);
        mainpanel.add(startpanel, "StartScreen");
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "StartScreen");
    }

    public void exitStartEnterGame() {
        gamepanel = new Gamepanel(this);
        mainpanel.add(gamepanel, "GameScreen");
        mainpanel.remove(startpanel);
        mainpanel.revalidate();
        mainpanel.repaint();
        cardlayout.show(mainpanel, "GameScreen");
    }
}
