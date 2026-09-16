# SeoulTech SE Course Team Project - Tetris reference code.

추가 날짜: 09.16<br>
작성자: 최승빈

게임 구조에 대한 기본적인 골자를 짜기 위해서 제가 생각한 방안을 적어보았습니다.

기본적으로 게임을 실행하기 위한 창으로 ```JFrame```를 상속하는 ```Maincontainer.java```를 만들고, 해당 객체에서 ```JPanel```을 상속하는 시작 화면과 메뉴 화면, 게임 화면을 ```src.seoultech.se.tetris.component```에 새롭게 정의하여, 이를 ```Maincontainer``` 객체에서 띄우는 형식으로 진행하는 것으로 진행해보았습니다.

각각의 panel들을 객체 생성 시에 maincontainer를 인자로 넘겨줘서 외부에서도 해당 객체를 지우고 진행 상태를 공유할 수 있게 하는 것으로 짜보았습니다.