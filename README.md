# SeoulTech SE Tetris

Java Swing으로 만든 테트리스다. 혼자서는 일반·아이템 모드를 즐길 수 있다. 두 사람은 한 키보드 또는 같은 네트워크의 두 PC에서 일반·아이템·시간제한 대전을 할 수 있다.

## 평가용 앱 실행

평가 환경은 **macOS 26 이상 · Apple Silicon**이다. 현재 검토용 앱은 로컬에서 실행됐지만 다운로드한 앱에 적용되는 macOS 배포 검사는 공증 티켓 누락으로 거부됐다. 공개 제출 전 확인 항목은 [최종 인수 기록](docs/final-acceptance.md)에 있다.

공개용 후보 묶음은 압축을 풀고 `app/` 안의 실행용 ZIP을 한 번 더 푼 다음 `SeoulTech Tetris.app`을 더블클릭한다. 실행용 ZIP만 받았다면 그 파일만 풀면 된다. 앱에 Java 21이 들어 있다. 두 PC는 같은 커밋에서 빌드한 각 운영체제용 앱을 사용한다.

소스에서 직접 실행하려면 JDK 21로 저장소 루트에서 `./gradlew run`을 실행한다. Apple Silicon Mac에서 `./gradlew packageRelease`를 실행하면 `build/distributions/seoultech-tetris-0.1.0-macos-aarch64.zip`과 SHA-256 파일이 생긴다.

| 플레이 방식 | 시작 메뉴에서 할 일 | 확인할 동작 |
|---|---|---|
| 1인 | `CLASSIC` 또는 `ITEM` 선택 | 방향키로 이동·회전하고 Space로 떨어뜨린다. 순위에 든 점수는 게임오버 후 이름을 입력해 저장한다. |
| 로컬 2인 | `LOCAL BATTLE` → 모드 선택 | 왼쪽 1P는 방향키·Space, 오른쪽 2P는 WASD·X로 조작한다. 승패 표시 후 `NEW MATCH`로 재대전한다. |
| 두 PC 대전 | 양쪽에서 `P2P BATTLE` → 한쪽은 `HOST GAME`, 상대는 표시된 IP로 `JOIN GAME` | 호스트가 모드를 고르고 둘 다 `START`를 누른다. 각자 왼쪽이 자기 보드다. 경기 종료 후 같은 로비에서 다시 시작할 수 있다. |

첫 실행의 기본 키 기준이다. `MENU`에서 키·난이도·화면 크기·색상을 바꿀 수 있다. IP 입력과 연결 복구는 [사용자 안내서](docs/user-guide.md#두-pc에서-p2p-대전)를 참고한다.

## 요구사항과 검증 자료

| 확인할 내용 | 문서 |
|---|---|
| 1인 게임, 난이도·아이템, 로컬·P2P 대전의 요구사항별 근거 | [1차 체크리스트](docs/requirements-1-checklist.md) · [2차 체크리스트](docs/requirements-2-checklist.md) · [3차 체크리스트](docs/requirements-3-checklist.md) |
| 5종 아이템의 모양·발동·점수, 대전 공격과 승패 | [게임 명세서](docs/specification.md) |
| 평가용 후보와 남은 실기 검증, 두 PC의 입력 지연 측정 | [최종 인수 기록](docs/final-acceptance.md) · [기존 P2P 측정 결과](docs/p2p-network-verification.md) |
| 개발 환경, 테스트, 팀원별 협업 이력 | [개발 안내서](docs/development.md) · [프로젝트 관리 기록](docs/project-management.md) |

1·2차 중간 제출본은 별도로 보존했다. [중간 릴리스 v0.1.0-req2.3](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.3) · [중간 검증 보고서](docs/verification.md)
