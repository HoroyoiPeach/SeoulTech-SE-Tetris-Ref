package seoultech.se.tetris.component;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.IBlock;
import seoultech.se.tetris.blocks.JBlock;
import seoultech.se.tetris.blocks.LBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.SBlock;
import seoultech.se.tetris.blocks.TBlock;
import seoultech.se.tetris.blocks.ZBlock;

public class Board extends JTextPane {

	private static final long serialVersionUID = 2434035659171694595L;
	
	public static final int HEIGHT = 20;
	public static final int WIDTH = 10;
	public static final char BORDER_CHAR = 'X';
	public static final int PLUSPOINT = 100;
	
	private Gamepanel gamepanel;
	private int[][] board;
	private Color[][] colors;
	private KeyListener playerKeyListener;
	private SimpleAttributeSet styleSet;
	private Timer timer;
	private Block curr;
	private StyledDocument doc;
	protected int score;
	protected Block next;
	int x = 3; //Default Position.
	int y = 0;
	
	private static final int initInterval = 1000;
	
	public Board(Gamepanel gamepanel) {
		//Board display setting.
		setEditable(false);
		setBackground(Color.BLACK);
		CompoundBorder border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Color.GRAY, 10),
				BorderFactory.createLineBorder(Color.DARK_GRAY, 5));
		setBorder(border);
		
		//Document default style.
		styleSet = new SimpleAttributeSet();
		StyleConstants.setFontSize(styleSet, 18);
		StyleConstants.setFontFamily(styleSet, "Monospaced");
		StyleConstants.setBold(styleSet, true);
		StyleConstants.setForeground(styleSet, Color.WHITE);
		StyleConstants.setLineSpacing(styleSet, -0.1f);
		StyleConstants.setAlignment(styleSet, StyleConstants.ALIGN_CENTER);

		this.gamepanel = gamepanel;
		//Set timer for block drops.
		timer = new Timer(initInterval, new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent e) {
				moveDown();
				drawBoard();
			}
		});
		
		//Initialize board for the game.
		board = new int[HEIGHT][WIDTH];
		colors = new Color[HEIGHT+2][WIDTH+3];
		playerKeyListener = new PlayerKeyListener();
		addKeyListener(playerKeyListener);
		setFocusable(true);
		requestFocus();
		for(int t=0; t < HEIGHT+2; t++) {
			for(int i=0; i < WIDTH+2; i++) {
				colors[t][i] = Color.WHITE;
			}
		}
		//Create the first block and draw.
	}

	public void boardStart() {
		curr = getRandomBlock();
		next = getRandomBlock();
		this.gamepanel.drawNextBoard();
		placeBlock();
		drawBoard();
		timer.start();
	}

	private Block getRandomBlock() {
		Random rnd = new Random(System.currentTimeMillis());
		int block = rnd.nextInt(7);
		switch(block) {
		case 0:
			return new IBlock();
		case 1:
			return new JBlock();
		case 2:
			return new LBlock();
		case 3:
			return new ZBlock();
		case 4:
			return new SBlock();
		case 5:
			return new TBlock();
		case 6:
			return new OBlock();			
		}
		return new LBlock();
	}
	
	private void placeBlock() {
		SimpleAttributeSet styles = new SimpleAttributeSet();
		StyleConstants.setForeground(styles, curr.getColor());
		for(int j=0; j<curr.height(); j++) {
			for(int i=0; i<curr.width(); i++) {
				if (curr.getShape(i, j) > 0) {
					board[y+j][x+i] += curr.getShape(i, j);
					colors[y+j+1][x+i+1] = curr.getColor();
				}
			}
		}
	}
	
	private void eraseCurr() {
		SimpleAttributeSet styles = new SimpleAttributeSet();
		StyleConstants.setForeground(styles, Color.WHITE);
		for(int j=0; j<curr.height(); j++) {
			for(int i=0; i<curr.width(); i++) {
				if (curr.getShape(i, j) > 0) {
					board[j+y][i+x] = 0;
					colors[j+y+1][i+x+1] = Color.WHITE; // TODO
				}
			}
		}
	}

	protected boolean canDown() {
		if(y < HEIGHT - curr.height()) {
			for(int j=0; j<curr.height(); j++) {
				for(int i=0; i<curr.width(); i++) {
					if ((curr.getShape(i, j) + board[y+j+1][x+i]) > 1) return false;
				}
			}
			return true;
		} else return false;
	}

	protected boolean canRight() {
		if(x < WIDTH - curr.width()) {
			for(int j=0; j<curr.height(); j++) {
				for(int i=0; i<curr.width(); i++) {
					if ((curr.getShape(i, j) + board[y+j][x+i+1]) > 1) return false;
				}
			}
			return true;
		} else return false;
	}

	protected boolean canLeft() {
		if(x > 0) {
			for(int j=0; j<curr.height(); j++) {
				for(int i=0; i<curr.width(); i++) {
					if ((curr.getShape(i, j) + board[y+j][x+i-1]) > 1) return false;
				}
			}
			return true;
		} else return false;
	}

	protected boolean canRotate() {
		boolean returned = false;
		int dx = ((x + curr.height()) > WIDTH) ? WIDTH - curr.height() : x;
		int dy = ((y + curr.width()) > HEIGHT) ? HEIGHT - curr.width() : y;
		for (int i = 0; i< curr.width(); i++) {
			for (int j = 0; j < curr.height(); j++) {
				int boardx = dx+curr.height()-j-1;
				int boardy = dy+i;
				if (((board[boardy][boardx] + curr.getShape(i, j)) > 1) && returned) return false;
				else if (((board[boardy][boardx] + curr.getShape(i, j)) > 1) && !returned) {
					dy -= (curr.width()-i);
					i = 0; j = 0;
					returned = true;
				}
			}
		}
		// ISSUE: 재활용 측면에서 고칠 필요가 존재
		x = dx;
		y = dy;
		return true;
	}

	protected void moveDown() {
		eraseCurr();
		if (canDown()) y++;
		else {
			placeBlock();
			eraseLine();
			isgameover();
			curr = next;
			next = getRandomBlock();
			this.gamepanel.drawNextBoard();
			x = 3;
			y = 0;
		}
		placeBlock();
	}

	protected void moveRight() {
		eraseCurr();
		if (canRight()) x++;
		placeBlock();
	}

	protected void moveLeft() {
		eraseCurr();
		if (canLeft()) x--;
		placeBlock();
	}

	protected void rotate() {
		eraseCurr();
		if (canRotate()) {
			curr.rotate();
		}
		placeBlock();
	}

	protected void drawBoard() {
		StringBuffer sb = new StringBuffer();
		for(int t=0; t<WIDTH+2; t++) sb.append(BORDER_CHAR); // 윗쪽 보더
		sb.append("\n");
		for(int i=0; i < board.length; i++) {
			sb.append(BORDER_CHAR); // 왼쪽 보더
			for(int j=0; j < board[i].length; j++) {
				if(board[i][j] == 1) {
					sb.append("O");
				} else {
					sb.append(" ");
				}
			}
			sb.append(BORDER_CHAR); // 오른쪽 보더
			sb.append("\n");
		}
		for(int t=0; t<WIDTH+2; t++) sb.append(BORDER_CHAR); // 아래쪽 보더
		this.setText(sb.toString());
		doc = this.getStyledDocument();
		doc.setParagraphAttributes(0, doc.getLength(), styleSet, false);
		int currentpoint = 0;
		for(int i=0; i < HEIGHT+2; i++) {
			for(int j=0; j < WIDTH+3; j++) {
				if(j == WIDTH+2) {
					currentpoint++;
					continue;
				}
				SimpleAttributeSet style = new SimpleAttributeSet();
				StyleConstants.setForeground(style, colors[i][j]);
				doc.setCharacterAttributes(currentpoint, 1, style, false);
				currentpoint++;
			}
		}
		this.setStyledDocument(doc);
	}
	
	protected void reset() {
		this.board = new int[HEIGHT][WIDTH];
	}

	protected void eraseLine() {
		int[] tmp;
		Color[] tmp_c;
		for (int i = 0; i < HEIGHT; i++) {
			int[] line = board[i];
			if (!Arrays.stream(line).anyMatch(t -> t == 0)) {
				for (int j = i - 1; j >= 0; j--) {
					tmp = board[j];
					tmp_c = colors[j];
					board[j+1] = tmp;
					colors[j+1] = tmp_c;
				}
				board[0] = new int[WIDTH];
				colors[0] = new Color[WIDTH+3];
				Arrays.fill(colors[0], Color.WHITE);
				this.score += PLUSPOINT;
				this.gamepanel.drawScore();
			}
		}
	}

	protected void isgameover() {
		OUTER:
		for (int i = 0; i < next.height(); i++) {
			for (int j = 0; j < next.width(); j++) {
				if ((board[i][j+3] + next.getShape(j, i) > 1) && (timer != null)) {
					timer.stop();
					gamepanel.gameOver();
					break OUTER;
				}
			}
		}
	}

	public class PlayerKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {
				
		}

		@Override
		public void keyPressed(KeyEvent e) {
			switch(e.getKeyCode()) {
			case KeyEvent.VK_DOWN:
				moveDown();
				drawBoard();
				break;
			case KeyEvent.VK_RIGHT:
				moveRight();
				drawBoard();
				break;
			case KeyEvent.VK_LEFT:
				moveLeft();
				drawBoard();
				break;
			case KeyEvent.VK_UP:
				rotate();
				drawBoard();
				break;
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
			
		}
	}
	
}
