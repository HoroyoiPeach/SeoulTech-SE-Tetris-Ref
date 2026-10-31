# RF-01 — 기존 동작과 계약 고정

작성: 2026-10-31 · 브랜치: `refactor/game-engine`

**게임 구조를 바꾸기 전에 기존 동작을 실행하고, 부족한 경계 테스트와 엔진 계약을 정리하는 단계다.** 제품 코드·빌드 설정·의존성은 변경하지 않았다.

## 기준과 환경

| 항목 | 값 |
|---|---|
| 원격 기준 | origin/main `695e725898ca081ec0b79a138b2f2c87e7de460e` |
| 작업 브랜치 시작점 | 원격 기준과 동일 |
| 운영체제 | macOS 26.6.2 / Apple Silicon arm64 |
| JDK | Eclipse Temurin 21.0.13+9 |
| Gradle | 저장소 Wrapper 9.8.2 |
| 데이터 | 기존 테스트의 임시 디렉터리 사용. 사용자 설정·점수 파일에 저장하지 않음 |

사용 가능한 JDK가 없어 [공식 Temurin 배포본](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)을 사용자 폴더에 풀어 사용했다. SHA-256은 `bf036b35b071bfb2f54f2c0af8ee8d88afa95ee6eb3dce02c0f94cfc2524d04a`와 일치했다. 시스템 기본 Java와 프로젝트 의존성은 바꾸지 않았다.

기존 미추적 문서는 보존했다. iCloud의 Git 객체 읽기 지연은 원격에서 다시 받은 동일 SHA-1 객체로 로컬 캐시를 복구해 해결했다. 브랜치 전환 뒤 제품 소스가 원격 기준과 동일한지 다시 확인했다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| 변경 전 `clean check build` | 성공: 98개 테스트, 실패·오류·건너뜀 0 |
| 변경 전 Line Coverage | 90.08% — 1,534 / 1,703 lines |
| 신규 경계 테스트 | 3개 테스트 메서드 / 파라미터 포함 5개 실행 사례 |
| 보강 후 전체 검사 | 성공: 103개 테스트, 실패·오류·건너뜀 0; Line Coverage 90.43% (1540/1703) |
| 컴파일 경고 | 기존 35개 유지(제품 32개·테스트 3개), 새 경고 0 |
| `desktopSmoke` | 실제 JFrame 생명주기 검사 성공 |
| `desktopFocusSmoke --args='swing'` | 실제 창의 Start·Settings·Classic·Item·이름 입력·점수판 포커스 경로 모두 성공 |
| `desktopFocusSmoke` | native Robot MENU press 검사 실패 — 아래 RF01-UI-01 |
| `desktopFocusSmoke --args='game'` | 게임 그룹만 실행해도 같은 위치에서 실패 |

합성 Swing 입력의 성공을 실제 물리 마우스 검증 성공으로 기록하지 않는다. 이번 실행은 macOS 기준이며 Windows CI를 새로 실행한 것은 아니다.

## 추가한 테스트

[RefactorBaselineCharacterizationTest.java](../../src/test/java/seoultech/se/tetris/component/RefactorBaselineCharacterizationTest.java)

| 메서드 | 고정한 동작 |
|---|---|
| normalClearHighlightExpiresWhilePausedWithoutMovingActiveBlock | 실행 중인 중력을 pause로 멈춰도 일반 줄 하이라이트는 만료되고 위치는 유지 |
| escapeKeyUsesConfiguredActionBeforeMenuExit | ESC가 게임 동작에 배정되면 그 동작을 수행하고, 미배정일 때 메뉴로 종료 |
| animatedItemCommitsOnlyAfterCompletionTick | Row·Column·Cross 각각 마지막 표시 프레임까지 격자·점수·next 유지, 완료 tick 뒤 효과 대상 셀 제거·한 번만 승계 |

타이머 콜백과 표시 시각을 통제해 검사하며, 정확한 실시간 대기 길이에 기대지 않는다. 기존 메뉴 종료 취소 테스트는 이미 score/current/next 보존과 공급 대기 초기화를 검증하므로 중복 작성하지 않았다.

## 기존 테스트 대응표

[rf01-test-migration.csv](rf01-test-migration.csv)에 JUnit 테스트 메서드 86개와 레거시 CHECKS 55개를 별도 행으로 기록했다. 전체 141행이며 **JUnit 실행 횟수와 표의 행수는 다르다.** 파라미터 테스트는 메서드 하나가 여러 사례로 실행된다.

| 범위 | 기존 근거 | 보완/이전 방향 |
|---|---|---|
| C01 이동·충돌·고스트 | RefactoringRegressionTest의 collision/rotation/preview CHECKS | 일반 엔진 추출 시 동일 격자·좌표·표시 결과 유지 |
| C02 점수·레벨·공급 | dropInputScore, lockLineLevel, lineClear 및 ItemSupplyAcceptanceTest | 엔진 적용 경계 검증. 독립 Difficulty 공식/분포 테스트는 유지 |
| C03 효과 프레임·확정 | Pr15BoardRegressionTest, LineItemAcceptanceTest, CrossItemAcceptanceTest | 이번 완료 tick 테스트 보강 후 Item 엔진으로 대응 |
| C04 일반 삭제 표시 | LineAnimationTest | pause 중 표시 만료를 이번 단계에서 보강 |
| C05 pause·취소·spawn 충돌 | Pr15BoardRegressionTest, ItemSupplyAcceptanceTest | 기존 취소·공급 시나리오 유지 후 엔진/Controller 경계에서 재검증 |
| C06 화면·키·기록 | DesktopFocusSmoke, DifficultyUiTest, ModeScoreUiTest | ESC 우선순위 보강. 실제 UI 검증은 엔진 추출 후에도 유지 |

아직 기존 테스트를 삭제하거나 이전하지 않았다. 이후에는 대응 테스트가 통과한 뒤 구조 의존 fixture를 교체한다.

## 고정한 입출력 계약

- [게임 엔진 계약](game-engine-contract.md): 명령, 상태, 고정 결과, 시간·취소·공급 순서.
- [Snapshot 예시](game-snapshot-example.json): 20×10 고정 셀과 현재/다음 블록을 분리한 읽기 전용 값 예시.

실제 엔진·Controller 구현은 RF-02 이후에 진행한다. RF-01에서 새 게임 규칙이나 대전 정책을 만들지 않는다.

## 별도 추적: RF01-UI-01

**현상:** native Robot의 MENU 버튼 누르기→밖으로 끌어 취소 시나리오에서 버튼의 pressed 상태를 확인하지 못한다.

**재현:** 전체 native 실행과 `--args='game'` 격리 실행이 모두 `DesktopFocusSmoke.checkGame:314`에서 실패했다. 처음에는 focus owner=Gamepanel, 격리 실행에서는 none이었다.

**확인된 범위:** 제품 코드 변경 전부터 발생했고, 일반 데스크톱 smoke와 실제 JFrame의 합성 입력 포커스 검사는 통과했다. 실패 지점은 native press 전달/버튼 상태 확인 경로다. OS 입력·포커스 환경과 제품 동작 중 어디가 원인인지는 아직 확정하지 않았다.

**처리:** 제품 코드나 검사 기준을 완화해 성공으로 만들지 않았다. 입력 연결을 바꾸는 RF-02에서 비교할 기존 실패로 보관한다. native/물리 마우스까지 검증 완료했다는 주장은 보류한다. 별도 native 입력 진단 또는 실제 입력 재검증이 필요하다.

## 재실행과 증거

Java 21의 JAVA_HOME을 지정한 뒤 저장소 루트에서 실행한다. 이번 로컬 실행 경로는 `/Users/henrykil/.local/share/codex-jdks/jdk-21.0.13+9/Contents/Home`이다.

```bash
./gradlew --no-daemon --console=plain clean check build
./gradlew --no-daemon --console=plain test --tests '*RefactorBaselineCharacterizationTest'
./gradlew --no-daemon --console=plain desktopSmoke
./gradlew --no-daemon --console=plain desktopFocusSmoke --args='swing'

# native 입력 제약 재현 — 성공 검사와 구분
./gradlew --no-daemon --console=plain desktopFocusSmoke --args='game'
```

보고서는 `build/reports/tests/test/`, `build/reports/jacoco/test/`에 생성된다. `clean` 또는 다음 실행 전에 보관한다. 이번 원본 로그·기준/최종 XML·화면 캡처는 작업공간의 `.omx/evidence/rf01-20261031/`에 별도 보관한다. 팀에 남기는 수치와 제한은 이 문서 및 [검증 요약 JSON](rf01-verification.json)에 기록한다.

커밋은 **테스트 보강 / 엔진 계약·예시 / 기준선·대응표**의 세 작업 단위로 분리한다. 기존 미추적 문서를 일괄 추가하지 않는다.
