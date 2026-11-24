# 기말 요구사항 대조표

**2026-11-24 검증 기준**은 게임 코드 `be8475a`다. macOS에서 JUnit 245개가 통과했고, 실패·건너뜀은 0개였다. 전체 제품 코드의 줄 커버리지는 3,961줄 중 3,564줄(89.98%)이다. 아래에서 자동검사 통과와 실제 두 PC의 인수 상태를 구분한다. [실행 기록](p2p-network-verification.md#브랜치-2-재검증--2026-11-24)

## 1·2차 기능 회귀

| 원문 | 확인한 동작 | 구현 · 검사 |
|---|---|---|
| 1차 2~14쪽 | 메뉴와 키보드 조작, 20×10 보드와 7종 블록, 낙하·회전·점수·줄 삭제, 설정·화면 크기·색각 모드, 이름과 순위 저장·재실행 | [게임 엔진][engine] · [점수 저장][scores] · [기본 회귀][reqtests] · [실제 창 검사][desktop] |
| 2차 2~6쪽 | 난이도 3종의 저장, I 블록의 난이도별 생성 분포, 속도와 줄 삭제 애니메이션, 모드별 점수 구분 | [블록 생성][generator] · [난이도별 분포 검사][distribution] · [화면·기록 검사][scoretest] |
| 2차 7~10쪽, 3차 13쪽 | 누적 10줄에 아이템 지급. L 행 삭제, O 무게추, 팀 아이템 C 열 삭제·B 폭발·P 행과 열 삭제의 발동·점수·누계 확인 | [5종 아이템 명세](specification.md#8-아이템-5종-명세--r2-p19r2-p20) · [공급 검사][supply] · [엔진의 아이템 검사][itemtest] |

세부 요구 번호별 기준은 [1차 체크리스트](requirements-1-checklist.md)와 [2차 체크리스트](requirements-2-checklist.md)에서 확인할 수 있다. 1차 15~19쪽의 성능·배포는 실행 기기 사양, 장시간 플레이 기록, 최종 패키지를 함께 확인한다. 기존 M1·RAM 8GB의 실행 기록과 과제에 제시된 최소 사양은 구분한다.

## 3차 기능과 검증 상태

| 원문 · 카드 | 수용 기준 | 구현 · 검사 | 확인 상태 |
|---|---|---|---|
| 2~5쪽 · R3-01 | 한 키보드에서 일반·아이템·시간제한 중 선택. 자기 키로 한쪽 보드만 움직이고 양쪽 보드·점수·예고를 본다. 종료 후 재대전하거나 메뉴로 복귀한다. | [로컬 대전 화면][local] · [세 모드 실제 창 검사][localtest] | macOS 실제 창에서 합성 입력으로 통과 |
| 3~6쪽 · R3-02 | 0·1줄은 공격 없음. 2줄 이상이면 마지막 조각의 칸을 비운 행을 순서대로 보낸다. 기존 대기 줄 아래에 추가해 10줄까지만 보관하고 상대가 블록을 고정한 뒤 다음 생성 전에 아래에서 한 번 올린다. | [공격 규칙][rules] · [대기열 경계 검사][attacktest] · [양방향 적용 검사][matchtest] | 엔진·로컬 자동검사 통과. 두 PC의 공격 표시 실기 대기 |
| 3·5쪽 · R3-03 | 아이템 대전에서도 일반 줄 공격 적용. 더 쌓을 수 없는 쪽이 지고 시간 만료 시 높은 점수가 이긴다. 팀 규칙은 만료 동점·동시 패배를 무승부로 처리하고 아이템 효과로 직접 지운 행을 공격에서 제외한다. | [경기 상태][match] · [승패 판정][judge] · [시간·동시 공격·효과 검사][matchtest] | 세 모드 자동검사 통과 |
| 8~10쪽 · R3-04 | 호스트 IP 표시, 클라이언트의 최근 성공 IP 복원. 호스트가 모드를 선택하고 두 명 모두 준비해야 시작한다. 양쪽 승패를 로비에 유지하고 재접속 없이 다시 시작한다. | [P2P 화면][panel] · [두 화면 버튼·키 검사][paneltest] · [재대전 검사][sessiontest] | 실제 TCP를 사용한 한 PC 자동검사 통과 |
| 9쪽 · R3-05 | 자기·상대 보드에 양쪽 입력과 경기 상태가 반영된다. **같은 LAN에서 키 입력부터 상대 화면 표시까지 양방향 200ms 이하** | [상태 동기화][session] · [패킷 변환 검사][codectest] · [측정 기록](p2p-network-verification.md) | 같은 Mac의 두 창에서 각 20회, 최대 24.83ms·27.84ms. **두 PC 실측 대기** |
| 10쪽 · R3-06 | 느려지면 경고, 단절되면 사유 표시 후 최초 접속 화면으로 복귀. 취소 후 재시도와 재접속 가능. 팀 기준은 왕복시간 999ms 이상 경고, 무수신 999ms 정지, 무수신 10초 종료다. | [통신 규칙](p2p-network-gui-specification.md) · [지속 지연][slowtest] · [한 방향 단절][cuttest] · [접속 취소][canceltest] | TCP 지연·단절·회복 자동검사 통과. 실제 LAN 저하 시연 대기 |
| 13~14쪽 · R3-07 | 기존 기능 유지, 단위 테스트·전체 줄 커버리지 70% 이상, 팀 아이템 명세, 기여·리뷰 이력, 실행 가능한 인수 버전 제공 | [Gradle 검사][gradle] · [OS별 CI][ci] · [팀 기여·리뷰](project-management.md#기말-개발과-리뷰) | macOS 자동검사와 압축 해제 후 앱 실행 확인. 최종 인수·배포 대기 |

3차 7·11쪽은 구현 팁이다. 12쪽의 컴퓨터 대전·채팅·다인 대전은 선택 과제이며 이번 제출 범위에서 제외했다.

## 최종 인수에 남은 증빙

- 같은 LAN의 서로 다른 두 PC에서 세 모드, 양방향 공격, 승패, 재대전, 1인 기록 보존, 단절 후 재접속을 실행한다. 기기·OS·게임 버전과 각 시나리오 결과를 남긴다.
- 양방향의 실제 키 누름과 상대 화면 변화를 한 영상에서 대조한다. 프레임 번호·촬영 FPS·방향별 지연값·최댓값과 네트워크 조건을 남긴다. 저하·단절 시연은 정상 LAN의 200ms 측정과 따로 기록한다.
- 최종 소스 커밋을 기준으로 Windows·macOS CI, 별도 JVM의 실행 데이터 수집, 최종 ZIP의 SHA-256·태그·런타임 동봉·다운로드 후 실행을 연결한다. 실제 두 PC 증빙과 팀원 리뷰를 대조한 뒤 [R3-07](https://github.com/users/HoroyoiPeach/projects/3/views/1?pane=issue&itemId=285513780)을 완료 처리한다.

[engine]: ../src/main/java/seoultech/se/tetris/game/GameEngine.java
[scores]: ../src/main/java/seoultech/se/tetris/score/ScoreStore.java
[reqtests]: ../src/test/java/seoultech/se/tetris/component/RequirementsRegressionTest.java
[desktop]: ../src/test/java/seoultech/se/tetris/component/DesktopSmoke.java
[generator]: ../src/main/java/seoultech/se/tetris/blocks/BlockGenerator.java
[distribution]: ../src/test/java/seoultech/se/tetris/blocks/BlockGeneratorDifficultyTest.java
[scoretest]: ../src/test/java/seoultech/se/tetris/component/ModeScoreUiTest.java
[supply]: ../src/test/java/seoultech/se/tetris/component/ItemSupplyAcceptanceTest.java
[itemtest]: ../src/test/java/seoultech/se/tetris/game/GameEngineItemTest.java
[local]: ../src/main/java/seoultech/se/tetris/component/LocalBattlePanel.java
[localtest]: ../src/test/java/seoultech/se/tetris/component/LocalBattleDesktopSmoke.java
[rules]: ../src/main/java/seoultech/se/tetris/attack/AttackRules.java
[attacktest]: ../src/test/java/seoultech/se/tetris/attack/AttackRulesTest.java
[match]: ../src/main/java/seoultech/se/tetris/game/BattleMatch.java
[matchtest]: ../src/test/java/seoultech/se/tetris/game/BattleMatchTest.java
[judge]: ../src/main/java/seoultech/se/tetris/game/MatchJudge.java
[panel]: ../src/main/java/seoultech/se/tetris/component/P2PPanel.java
[paneltest]: ../src/test/java/seoultech/se/tetris/component/P2PPanelTest.java
[session]: ../src/main/java/seoultech/se/tetris/network/P2PBattleSession.java
[sessiontest]: ../src/test/java/seoultech/se/tetris/network/P2PBattleSessionIntegrationTest.java
[codectest]: ../src/test/java/seoultech/se/tetris/network/BattleMessageCodecTest.java
[slowtest]: ../src/test/java/seoultech/se/tetris/network/P2PContinuousLatencyTest.java
[cuttest]: ../src/test/java/seoultech/se/tetris/network/P2PDirectionalDelayTest.java
[canceltest]: ../src/test/java/seoultech/se/tetris/network/P2PConnectionCancellationTest.java
[gradle]: ../build.gradle
[ci]: ../.github/workflows/ci.yml
