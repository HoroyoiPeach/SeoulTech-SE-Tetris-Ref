# MENU native 입력 누락 후속 수정

작성: 2026-11-01 · 상태: **수정 적용, 실제 입력 재검사 대기**

## 확인한 현상

- 기존 native 검사에서 Classic MENU의 누름 상태가 확인되지 않았다.
- 버튼은 활성·표시 상태였고 중앙 hit target과 MouseListener도 정상이었지만, 누름 이벤트가 관측되지 않았다.
- 클릭 검사를 왼쪽 버튼의 누름·뗌 모두 확인하도록 강화하자, `P`로 일시정지한 직후의 overlay 클릭에서도 실패했다.
- Robot 동기화 설정만 바꿔도 실패했다. 현재는 기존 타이밍 설정으로 되돌렸다.

## 수정한 경계

Gamepanel은 게임 조작과 기존 영문·숫자 이름 입력을 KeyListener로 직접 처리한다. 이 패널에 `enableInputMethods(false)`를 적용해 입력기의 문자 조합 처리를 분리했다. OS 전체의 입력기 설정은 변경하지 않는다.

OpenJDK의 [macOS mouseDown 처리](https://github.com/openjdk/jdk21u/blob/ba94eace682733f66e623c089bcafcf4a1b8e39c/src/java.desktop/macosx/native/libawt_lwawt/awt/AWTView.m#L165)는 입력기가 이벤트를 처리하면 Java에 누름을 전달하지 않고, 뗌은 전달한다. 관측한 현상과 일치하는 경로다. 다만 이번 실제 실패의 해결 여부는 수정 후 native 재검사로 확인해야 한다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| Gamepanel 이벤트가 문자 조합에 전달되지 않는 회귀 검사 | 수정 전 실패 → 수정 후 통과 |
| Classic·Item MENU 취소·정상 클릭·입력 유지 | 통과 |
| JDK 21 `check build` | 통과 |
| JUnit | 155개, 실패·오류·건너뜀 0개 |
| JaCoCo Line Coverage | 1,697 / 1,872 = 90.65% |
| 수정 후 native Robot | 사용자 입력을 비우는 시간 확인 후 실행 예정 |

회귀 검사는 문자 조합이 게임 이벤트를 소비할 수 없다는 경계를 확인한다. native 성공을 대신하는 검사는 아니다. MENU 누름 상태 검사는 유지했고, 기존 클릭 검사도 누름·뗌 둘 다 확인하도록 강화했다.

## 커밋과 증거

- `d2cd797`: pause/resume 후 MENU 취소·정상 클릭 회귀 검사
- `79fc49b`: 게임 조작의 문자 조합 처리 분리 및 수정 전 실패 검사
- `ce4ec2c`: native 클릭의 왼쪽 누름·뗌 확인과 실패 진단 보강

로그와 검사 결과는 작업 공간 `.omx/evidence/menu-native-fix-20261101/`에 보관한다. 독립 버튼 진단은 캡처가 대상 창을 보여주지 않아 원인 판단 근거에서 제외했다. 실제 게임 창의 기록과 수정 전후 회귀 검사만 근거로 사용한다.
