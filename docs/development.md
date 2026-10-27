# SeoulTech SE Tetris 개발 안내서

이 문서는 공개 릴리스 `v0.1.0-req2.3`의 개발·빌드 기준을 설명한다.

제출 소스는 `main`의 `10d7b78`이며, 제품 코드는 `154fd09`와 같다.

최종 제출 대상은 macOS 26 이상 Apple Silicon이다.

## 1. 개발 환경

| 항목 | 기준 |
|---|---|
| Java | JDK 21 toolchain, `--release 21` |
| Gradle | Wrapper 9.8.2 |
| 문자 인코딩 | Java 컴파일 UTF-8, 패키지 JVM UTF-8 |
| 테스트 | JUnit Jupiter 5.14.1 |
| 커버리지 | JaCoCo 0.8.15, 전체 main 코드 Line Coverage 70% gate |
| UI | Java Swing/AWT |
| 네이티브 패키지 | JDK 21의 `jpackage`, macOS 호스트 |

시스템에 Gradle을 따로 설치하지 않고 저장소의 Wrapper를 사용한다.<br>
Wrapper가 Java를 찾지 못하면 JDK 21 경로를 `JAVA_HOME`에 지정한다.

```bash
export JAVA_HOME=/absolute/path/to/jdk-21
./gradlew --version
```

프로젝트가 별도로 요구하는 환경 변수는 없다.<br>
실행 데이터 경로를 격리할 때는 JVM 시스템 속성 `tetris.dataDir`을 사용한다.

## 2. 기본 명령

macOS·Linux 계열 셸:

```bash
./gradlew --no-daemon --console=plain clean build
./gradlew --no-daemon --console=plain run
```

`build`는 컴파일·JUnit·JaCoCo 보고서·70% Line Coverage gate를 실행한다.<br>
네이티브 UI 검사와 `jpackage` 패키징은 별도 task다.<br>
의존성이 로컬 캐시에 모두 있으면 `--offline`을 추가할 수 있다.

## 3. 테스트와 보고서

### JUnit·커버리지

```bash
./gradlew --no-daemon --console=plain test
./gradlew --no-daemon --console=plain jacocoTestReport jacocoTestCoverageVerification
```

주요 결과 위치:

```text
build/test-results/test/             JUnit XML
build/reports/tests/test/            JUnit HTML
build/reports/jacoco/test/html/      JaCoCo HTML
build/reports/jacoco/test/jacocoTestReport.xml
build/reports/req2/block-distribution.csv
build/reports/req2/item-distribution.csv
```

`test`는 `java.awt.headless=true`로 실행된다.<br>
기존 독립 회귀 프로그램은 `LegacyRegressionTest` 한 개의 JUnit 어댑터를 통해 별도 JVM에서 실행되고 같은 JaCoCo 파일에 append한다.<br>
보고할 때 JUnit 개수와 그 안에서 실행된 기존 회귀 항목을 단순 합산하지 않는다.

### 확률 분포

일반 블록 분포 검사는 실제 `BlockGenerator.nextBlock(difficulty)`를 난이도마다 100,000회 호출한다.

- seed: `20261024`
- Easy 가중치: I 12, 나머지 여섯 종류 각각 10
- Normal 가중치: 일곱 종류 각각 1
- Hard 가중치: I 8, 나머지 여섯 종류 각각 10
- 판정: `|관측확률 - 기대확률| / 기대확률 ≤ 0.05`

아이템 분포 검사는 실제 `BlockGenerator.nextItemBlock()`을 100,000회 호출한다.

- seed: `20261025`
- L/O/C/B/P 기대확률: 각각 20%
- 판정: 상대오차 5% 이하

두 CSV에는 seed·반복 수·기대/관측 확률·오차·판정이 들어간다.<br>
원문 최소 반복 수 1,000회와 현재 100,000회 검증량을 구분해서 보고한다.

### 핵심 연산 성능

`PerformanceRegressionTest`는 빈 20×10 보드에서 일곱 블록의 아래·왼쪽·오른쪽·회전 판정을 각 100,000회, 총 400,000회 실행한다.<br>
팀 자동 기준은 5초 미만이고 테스트 timeout은 10초다.<br>
이 검사는 실제 창 렌더링·물리 입력·전체 플레이 성능을 대신하지 않는다.

## 4. 실제 창 검사

두 task 모두 표시 가능한 데스크톱 세션에서 실행한다.

```bash
./gradlew --no-daemon --console=plain desktopSmoke
./gradlew --no-daemon --console=plain desktopFocusSmoke
```

| task | 확인 범위 | 결과 위치 |
|---|---|---|
| `desktopSmoke` | 실제 JFrame, 메뉴·반복 합성 입력·정지/재개·B/P 아이템·설정/기록 재로드·순위·세 크기·네 팔레트 | `build/reports/desktop/` |
| `desktopFocusSmoke` | 활성 창에서 클릭/포커스 뒤 입력, 설정·게임·순위 화면 | `build/reports/desktop-focus/` |

`desktopFocusSmoke`는 기본적으로 AWT `Robot`을 사용한다.<br>
화면 그룹을 제한하거나 Swing 이벤트 경로를 확인할 수 있다.

```bash
./gradlew desktopFocusSmoke --args="start"
./gradlew desktopFocusSmoke --args="game scoreboard"
./gradlew desktopFocusSmoke --args="swing all"
```

그룹은 `start`, `game`, `scoreboard`, `all`만 허용한다.<br>
`swing` 옵션은 실제 물리 키보드 검증이 아니라 실제 포커스 라우팅 위의 합성 Swing 이벤트 검증이다.<br>
현재 CI의 `desktopSmoke`도 합성 KeyListener 입력을 사용하므로 실제 키보드·마우스와 최종 설치 환경은 별도로 인수 검증한다.

## 5. 실행 데이터 격리

| 실행 형태 | 기본 위치 |
|---|---|
| Gradle `run`·일반 클래스 실행 | 현재 작업 디렉터리의 `setting.txt`, `tetris_score.txt` |
| `jpackage` 앱 | `${user.home}/.seoultech-tetris/` |
| 테스트·임시 실행 | `-Dtetris.dataDir=/absolute/path`로 지정한 디렉터리 |

테스트는 실제 사용자 데이터를 건드리지 않도록 임시 디렉터리를 지정한다.<br>
패키지 launcher는 `-Dtetris.packaged=true`를 자동 전달한다.

직접 Java 명령을 사용할 때의 예:

```bash
java -Dtetris.dataDir=/tmp/tetris-data -jar build/libs/seoultech-tetris.jar
```

## 6. 네이티브 패키징

`packageRelease`는 macOS 앱 이미지·portable ZIP·SHA-256을 만든다.

```bash
./gradlew --no-daemon --console=plain clean build desktopSmoke packageRelease
```

task 흐름:

```text
jar
  → preparePackageInput
  → packageAppImage
  → packagePortable
  → packageChecksum
  → packageRelease
```

출력:

```text
build/distributions/seoultech-tetris-0.1.0-<platform>-<architecture>.zip
build/distributions/seoultech-tetris-0.1.0-<platform>-<architecture>.zip.sha256
```

- `<platform>`: `macos`
- `<architecture>`: `aarch64` 또는 JVM이 보고한 기타 아키텍처
- 앱 이름: `SeoulTech Tetris`
- macOS bundle: `SeoulTech Tetris.app`
- 아이콘: macOS `tetris.icns`
- Java 21 runtime: 앱 이미지 내부 포함

macOS는 `ditto`로 ZIP을 만든다.<br>
패키징은 빌드 호스트와 같은 OS용으로만 수행된다.

최종 제출에서는 소스 커밋, CI run/job, ZIP 파일명·SHA-256과 대상 OS/CPU를 같은 버전으로 묶는다.
실제 tag·ZIP·SHA-256·CI·인수 결과는 [GitHub Releases](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases)의 해당 릴리스 본문에 기록한다.

## 7. 자동화 구성과 공개 릴리스 결과

`.github/workflows/ci.yml`은 push와 pull request에서 Java 21, Gradle Wrapper, JUnit, JaCoCo, GUI smoke와 호스트별 패키징을 실행한다.<br>
보고서와 패키지는 workflow artifact로 7일간 보관한다.<br>
`desktopFocusSmoke`는 표시 가능한 로컬 환경에서 별도로 실행한다.

공개 릴리스 제품 코드 `154fd09`의 macOS 로컬 결과:

| 항목 | 결과 |
|---|---|
| JUnit | 98개, 실패·오류 0 |
| Line Coverage | 1534/1703 = **90.08%** |
| 70% gate | 통과 |
| 일반·아이템 분포 | 난이도별 100,000회·5종 100,000회, 전체 통과 |
| 핵심 연산 성능 | 400,000회 26ms / 5,000ms 기준 통과 |
| `desktopSmoke` | PASS |
| `packageRelease` | macOS aarch64 ZIP·runtime·아이콘·런처 생성 확인 |
| 실행 ZIP SHA-256 | `87a8402eb40931f34052c5880f504c33d96c28c37635bca6dde99c616bb2e301` |

실행 명령은 `./gradlew --no-daemon --console=plain build desktopSmoke packageRelease`였다.
원본 결과는 [자동검증 증빙](evidence/req12-154fd09-local/)에 보관했다.
배포 파일과 전체 제출 ZIP의 식별 정보는 [v0.1.0-req2.3 릴리스](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.3)에서 확인한다.
검사 범위와 수동 실행 결과는 [검증 보고서](verification.md)를 기준으로 한다.

## 8. 코드 구조

| 영역 | 주요 클래스 | 책임 |
|---|---|---|
| 실행 진입 | `main/Tetris` | Swing EDT에서 `Maincontainer` 생성 |
| 화면·설정 | `component/Maincontainer` | 화면 전환, 설정 로드·적용·저장, 색상 변환 |
| 메뉴 UI | `Startpanel`, `Settingpanel`, `Scoreboardpanel`, `MenuButtons` | 메뉴·설정·모드별 순위 |
| 게임 UI | `Gamepanel`, `Nextblockpanel` | 입력, 점수·예고, 정지·게임오버·이름 입력 |
| 게임 상태 | `Board` | 보드·타이머·낙하·고정·행 삭제·레벨·점수·아이템 효과 |
| 충돌 | `rules/CollisionRules` | 이동·회전 위치 판정 |
| 블록·생성 | `blocks/Block` 하위 클래스, `BlockGenerator` | shape·text·color·회전·난이도별 선택 |
| 저장 | `ScoreStore`, `DataPaths` | 순위·모드 필터, 쓰기 가능한 데이터 경로 |

보드와 다음 블록은 Monospaced 문자·색상으로 렌더한다.<br>
`shape`는 점유 여부, `text`는 실제 표시 문자를 담당한다.<br>
색상 모드는 주요 색을 `Maincontainer.changeColor`로 변환한다.

## 9. 설정·메뉴 확장

설정 추가 시 다음을 함께 수정한다.

1. `Maincontainer.Setting.validSettingMap`에 허용 값과 기본값 추가
2. `Settingpanel`에 선택 UI 등록
3. `Maincontainer.applySetting()`에서 런타임 상태 반영
4. 구형 파일의 누락 항목이 기본값을 유지하는지 확인
5. 저장·재로드·기본값·잘못된 값 테스트 추가

메뉴는 `MenuButtons.add(parent, child)` 순서대로 등록되고 위·아래 순환과 선택을 공통 처리한다.<br>
항목 추가 후 최소 창 크기에서도 가시성을 확인한다.

## 10. 블록·아이템 확장

일반 블록을 추가하려면 `Block` 하위 클래스, `BlockGenerator.createBlock()`, 난이도별 가중치, 회전·충돌·미리보기·색상·분포 테스트를 함께 수정한다.<br>
현재 분포 코드는 일곱 종류를 전제로 한다.

Item 모드 아이템은 블록 클래스·생성기·보드 효과 처리를 연결해 추가한다.<br>
추가 절차는 다음과 같다.

1. `ItemBlock` 하위 클래스에 shape·text·color·회전·`createNew()` 정의
2. `BlockGenerator.nextItemBlock()` 선택 분기와 확률 갱신
3. `Board.useItem()`에 타입별 효과 추가
4. 특수 이동·회전 제약이 있으면 `Board` 관련 분기 확인
5. `ItemDistributionTest`의 식별자·확률 갱신
6. 발동·경계·점수·줄 누계·중첩·정지/종료·예고 회귀 추가
7. [게임 명세서](specification.md)와 [요구사항 2 체크리스트](requirements-2-checklist.md) 갱신

P 아이템의 실제 변경 면:

| 파일 | 역할 |
|---|---|
| `blocks/CrossBlock.java` | 2×2 `P` shape·색상·회전 불변 |
| `blocks/BlockGenerator.java` | L/O/C/B/P 중 선택 |
| `component/Board.java` | 두 행·두 열 효과, 행 점수·누계, 중복 행, 애니메이션 |
| 공통 미리보기·렌더 경로 | shape·문자·색 표시 |
| `CrossItemAcceptanceTest`, `ItemSupplyAcceptanceTest`, `ItemDistributionTest` | 효과·공급·분포 회귀 |

아이템 추가 시 `Board`·생성기·시험·명세를 함께 갱신한다.

B·C 효과는 대상 셀을 비운 뒤 `Board.settleColumns()`로 모든 열을 독립적으로 정착시킨다.
정착은 열 간 이동 없이 세로 순서·문자·색상을 보존한다.
B 정착으로 생긴 완성 행은 공통 `eraseLine()` 경로에서 기존 점수·줄 누계·아이템 공급을 적용한다.
C가 지운 열은 비어 있으므로 정착만으로 새 완성 행을 만들지 않는다.
P는 기존 행 삭제·압축 경로를 유지한다.

근거: [B·C 효과와 정착](../src/main/java/seoultech/se/tetris/component/Board.java#L269), [B·C 인수 테스트](../src/test/java/seoultech/se/tetris/component/ColumnBombAcceptanceTest.java#L268).

## 11. 변경 검증 순서

1. 변경 동작의 직접 JUnit
2. 전체 `build`와 70% Line Coverage gate
3. GUI 영향 시 `desktopSmoke`
4. 클릭·포커스·물리 입력 영향 시 표시 가능한 환경에서 `desktopFocusSmoke`
5. 제출 후보면 대상 OS에서 `packageRelease`
6. ZIP SHA-256 확인
7. 최종 ZIP으로 더블클릭·입력·저장·완전 종료 후 재실행 수동 검증

자동검사와 배포 검증 결과는 검증 보고서에서 관리한다.

## 12. 수업 내용의 실제 적용

이 표는 코드·자동화에서 직접 확인할 수 있는 사실만 정리한다.<br>
팀 합의 과정·개인별 기여·브랜치/PR 운영 정책은 [프로젝트 관리 기록](project-management.md)에서 다룬다.

| 수업 개념 | 실제 문제·선택 | 코드·검증 근거 |
|---|---|---|
| 버전 관리 | 소스·테스트·아이콘·Wrapper·CI를 같은 저장소에서 변경 이력으로 관리 | Git 이력, `src/`, `gradle/wrapper/`, `.github/workflows/ci.yml` |
| 책임 분리 | 화면 전환·게임 상태·충돌·생성·저장을 클래스로 나눔 | `Maincontainer`, `Board`, `CollisionRules`, `BlockGenerator`, `ScoreStore` |
| 회귀 테스트 | 기존 기능과 요구사항 2의 난이도·모드·아이템 변경을 함께 검증 | `src/test/java`, `LegacyRegressionTest`, 아이템 acceptance test |
| 반복 가능한 빌드 | 개발자 환경 차이를 줄이기 위해 Java toolchain과 Gradle Wrapper 고정 | `build.gradle`, `gradle-wrapper.properties` |
| 지속적 통합 | push·PR마다 두 OS matrix에서 build·GUI smoke·패키징 실행 | `.github/workflows/ci.yml` |
| 커버리지 | 직접 작성한 main 코드 전체의 Line Coverage를 측정하고 70% gate 적용 | `jacocoTestReport`, `jacocoTestCoverageVerification`; 제외 목록 없음 |
| 비기능 측정 | 막연한 “빠름” 대신 핵심 이동·회전 판정 400,000회/5초의 팀 기준을 자동화 | `PerformanceRegressionTest`; 실제 GUI·물리 입력 성능은 별도 검증 |
| 확률 검증 | 실제 생성기의 기대 분포를 seed·반복 수·상대오차 식으로 재현 | `BlockDistributionTest`, `ItemDistributionTest`, 생성된 CSV |
| 배포 자동화 | 소스에서 host-native 앱·runtime·아이콘·ZIP·SHA-256을 한 task로 생성 | `gradle/packaging.gradle`, `packageRelease` |

커버리지 70%는 현재 자동 gate이고 요구사항 2에서 기말 기준으로 명시된 값이다.

중간평가 기준은 50%다.

성능 5초 기준은 핵심 연산 회귀 검사에 적용한다.

## 13. 버전과 릴리스 식별

공개 제출본은 다음 값으로 고정한다.

| 항목 | 값 |
|---|---|
| 릴리스 | [`v0.1.0-req2.3`](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.3) |
| 병합 PR | [#30](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/30) |
| 제출 소스 | `10d7b78fbea00ce57be75d7f966e4fd13c00b818` |
| 제품 코드 | `154fd097deff2675e7030f607b0ceeb35b9e69c5` |
| 실행 ZIP | `seoultech-tetris-v0.1.0-req2.3-macos-aarch64.zip` |
| 실행 ZIP SHA-256 | `87a8402eb40931f34052c5880f504c33d96c28c37635bca6dde99c616bb2e301` |
| 제출 ZIP | `seoultech-tetris-v0.1.0-req2.3-submission.zip` |
| 제출 ZIP SHA-256 | `b610cc1b95ddd13f4ac67b1db4cec3b9dbad82c5a5da992841bbf8411f94de62` |

후속 릴리스에는 다음 값을 함께 남긴다.

```text
source commit / branch 또는 tag
Java·Gradle 버전
대상 OS·버전·CPU
CI run/job URL
JUnit 수·실패·skip
Line Coverage covered/missed와 범위
분포 CSV seed·반복 수·계산식
ZIP 파일명·SHA-256
수동 검증 환경·실제 날짜·기기·결과
검증 범위·참고 사항
```

서로 다른 커밋의 소스·CI·패키지를 같은 버전으로 표현하지 않는다.

## 관련 문서

- [제출 안내](../README.md)
- [요구사항 1 체크리스트](requirements-1-checklist.md)
- [요구사항 2 체크리스트](requirements-2-checklist.md)
- [게임 명세서](specification.md)
- [사용자 안내서](user-guide.md)

## 구현·빌드 근거

- [Gradle 빌드와 검증 task](../build.gradle)
- [네이티브 패키징 task](../gradle/packaging.gradle)
- [Windows·macOS CI](../.github/workflows/ci.yml)
- [화면 전환·설정](../src/main/java/seoultech/se/tetris/component/Maincontainer.java)
- [보드·아이템 효과](../src/main/java/seoultech/se/tetris/component/Board.java)
- [충돌 규칙](../src/main/java/seoultech/se/tetris/rules/CollisionRules.java)
- [블록 생성기](../src/main/java/seoultech/se/tetris/blocks/BlockGenerator.java)
- [P 아이템](../src/main/java/seoultech/se/tetris/blocks/CrossBlock.java)
- [일반 블록 분포 검사](../src/test/java/seoultech/se/tetris/blocks/BlockDistributionTest.java)
- [아이템 분포 검사](../src/test/java/seoultech/se/tetris/blocks/ItemDistributionTest.java)
- [핵심 연산 성능 검사](../src/test/java/seoultech/se/tetris/component/PerformanceRegressionTest.java)
- [실제 창 smoke](../src/test/java/seoultech/se/tetris/component/DesktopSmoke.java)
- [포커스 smoke](../src/test/java/seoultech/se/tetris/component/DesktopFocusSmoke.java)

- [검증 보고서](verification.md)
- [프로젝트 관리 기록](project-management.md)
