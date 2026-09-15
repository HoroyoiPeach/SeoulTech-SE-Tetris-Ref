Generate Board:
- JTextPane 생성, 기본값은 background: black
- 두 개의 보더 생성, 회색과 짙은 회색을 보더로 만들어 외곽의 테두리 생성
- pane에 보더 병합 후, Board 객체의 .getContentPane()에 pane 추가
- styleset의 기본값은 FontSize: 18, FontFamily: Courier, Bold, Foreground: WHITE, Alignment: ALIGN_CENTER
- timer 추가, 1000틱 마다 이벤트를 실행하며 실행되는 명령어는 moveDown(), drawBoard()
- 높이 20, 너비 10의 2차원 board 생성하기
- playerKeyListener 추가

Generate PlayerKeyListener:
- 키 입력시, 각각에 맞는 moveLeft(), moveRight(), moveDown(), rotate() 실행