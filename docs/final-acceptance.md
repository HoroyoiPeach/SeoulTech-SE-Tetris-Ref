# 기말 제출 후보 인수 기록

평가 대상은 Java 21 런타임을 포함한 **macOS 26 이상 · Apple Silicon** 앱이다. 후보 묶음의 `manifest.json`에서 실제 소스 커밋, 앱 ZIP의 SHA-256, 양쪽 OS의 검사 결과와 GitHub Actions 실행 주소를 확인한다. 중간 제출 태그 `v0.1.0-req2.3`의 결과는 [이전 검증 보고서](verification.md)에 보관한다.

## 평가자가 확인할 순서

1. 후보 ZIP을 풀고 `app/` 안의 macOS ZIP도 압축 해제한다. `SeoulTech Tetris.app`을 더블클릭한다. 메뉴에서 `CLASSIC` 또는 `ITEM`으로 1인 게임을 확인한다.
2. `LOCAL BATTLE`에서 한 키보드로 세 모드를 확인한다. 두 PC 대전은 같은 후보 커밋의 패키지를 양쪽에서 열고 `P2P BATTLE`을 선택한다. 창 크기·키 설정·승패·재대전은 [사용자 안내서](user-guide.md)에 정리했다.
3. [두 PC LAN 인수검사](lan-acceptance.md)로 양방향 키 입력, 공격, 재대전과 단절 복구를 확인하고 원본 영상을 보관한다. 입력부터 상대 모니터 표시까지 200ms 이하인지 방향별로 기록한다.

## 확인한 범위와 남은 증빙

| 항목 | 확인 방법 · 기록 |
|---|---|
| 1·2·3차 기능, 다섯 아이템과 대전 규칙 | [요구사항별 코드·검사](requirements-3-checklist.md) · [아이템 명세](specification.md) · [팀 기여와 리뷰](project-management.md) |
| 자동 회귀·커버리지·실제 창 | 후보 커밋에서 Windows·macOS의 `build desktopSmoke packageRelease`가 통과해야 묶음을 생성한다. OS별 JUnit 원본, JaCoCo XML·HTML, 화면 검사·분포 자료는 ZIP의 `checks/`에 들어간다. `build`는 전체 줄 커버리지 70%와 별도 JVM의 회귀 검사 커버리지 수집을 검사한다. |
| 같은 컴퓨터의 P2P 검증 | 게임 코드 `be8475a`에서 Mac 두 창의 방향별 20회 최댓값은 24.83ms와 27.84ms였다. 측정 조건과 지연·단절 재현은 [P2P 검증 기록](p2p-network-verification.md)에 있다. |
| 실물 두 PC와 최소 사양 | 서로 다른 PC에서 세 모드·양방향 200ms 원본 기록이 남아 있다. M1·8GB 실행 기록만으로 CPU 1.2GHz·RAM 1GB·여유 디스크 500MB 경계에서의 성능을 확정할 수 없다. 실측 기기와 확인 가능한 한계를 함께 적는다. |
| 최종 제출 | 팀이 실제 두 PC 결과와 동료 리뷰를 확인한 뒤 승인한 커밋에 태그를 붙이고, 그 커밋의 후보 ZIP·체크섬을 함께 제공한다. 공개 배포본을 다시 받아 앱 실행과 SHA-256을 확인한 뒤 제출 이력에 연결한다. |

## 같은 커밋으로 묶음 만들기

팀 저장소에서 후보 커밋을 체크아웃한다. 해당 커밋을 푸시한 **Java CI** 실행이 양쪽 OS에서 성공하면, 저장소 루트에서 Python 3와 팀 저장소 아티팩트를 읽을 수 있는 GitHub CLI로 실행한다.

```shell
python3 tools/release/bundle_candidate.py RUN_ID
```

`RUN_ID`는 Actions 실행 주소 끝의 숫자다. 출력은 `build/final-candidate/` 아래의 후보 ZIP과 SHA-256 파일이다. 새 실행에서 받아 다시 묶을 때는 해당 실행 ID의 파일을 사용한다.

| ZIP 안의 경로 | 내용 |
|---|---|
| `project/` | 그 커밋에 포함된 소스·리소스·테스트·개발 및 평가 문서 |
| `app/` | 같은 CI에서 빌드한 macOS 앱 ZIP. 내부 압축을 유지해 앱 서명·권한·런타임을 보존한다. |
| `checks/macos-26/` · `checks/windows-2025/` | 각 운영체제의 실제 검사 결과 |
| `manifest.json` | 커밋·브랜치·CI 실행·패키지 해시와 운영체제별 테스트 수·줄 커버리지 원시 수치 |
