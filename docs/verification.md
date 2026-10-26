# 검증 보고서

이 문서는 요구사항 1·2 구현에 대한 자동검증 결과와 원본 증빙을 연결한다.<br>
공식 제출 환경인 **macOS 26 이상**의 결과를 기준으로 작성했다.

## 1. 검증 기준

| 항목 | 값 |
|---|---|
| 실행 날짜 | 2026-10-26 |
| 운영체제 | macOS 26.6.2, aarch64 |
| Java | 21.0.13 |
| 제품 소스 | `154fd097deff2675e7030f607b0ceeb35b9e69c5` |
| 변경 범위 | B·C 효과 후 열별 정착, O 밝은 회색, P 기존 동작 유지 |
| 실행 환경 | 로컬 macOS · GitHub Actions 결과와 구분 |
| 소스 대조 | [소스 해시](evidence/req12-154fd09-local/source.json) · 커밋의 제품·테스트·빌드 파일과 대조 |
| 실행 로그 | [build.log](evidence/req12-154fd09-local/build.log) |

다음 명령으로 테스트·커버리지·GUI 검사·실행 ZIP 생성을 확인했다.

```bash
./gradlew --no-daemon --console=plain build desktopSmoke packageRelease
```

PR의 macOS CI 결과와 실제 배포 태그·ZIP은 릴리스 본문에서 이 증빙과 함께 연결한다.
이전 `e19f900`의 [CI 결과](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/actions/runs/40315760987)와 [원본](evidence/req12-e19f900/summary.json)은 변경 전 이력으로 보관한다.

## 2. 결과 요약

| 검증 항목 | 결과 | 판정 |
|---|---:|---|
| JUnit | 98개 실행 · 실패 0 · 오류 0 · 건너뜀 0 | 통과 |
| Line Coverage | 1,534 / 1,703 = **90.08%** | 70% 기준 통과 |
| 일반 블록 분포 | 3난이도 × 100,000회 · 21개 조합 | 모두 통과 |
| 아이템 분포 | 100,000회 · 5종 | 모두 통과 |
| 핵심 이동·회전 판정 | 400,000회 · 26ms | 5,000ms 기준 통과 |
| 실제 JFrame 자동검사 | 메뉴·게임·아이템·설정·순위·화면 크기·팔레트 | 통과 |
| macOS 패키징 | 앱·런처·아이콘·Java runtime·ZIP | 생성·구성 확인 |

원본 XML·CSV와 이미지의 해시는 [`summary.json`](evidence/req12-154fd09-local/summary.json)에 기록했다.

## 3. 단위·회귀 검사와 커버리지

JUnit XML 22개에서 테스트 98개가 모두 통과했다.<br>
난이도·아이템 효과·공급·모드별 기록·설정 저장·예외 처리와 기존 기능 회귀를 포함한다.
이번에는 B·C 정착의 문자·색상·순서 보존과 B 정착 후 줄 점수·아이템 공급 검사를 추가했다.
O는 4개 팔레트의 렌더링으로 확인했고, P의 기존 삭제·행 압축 검사도 통과했다.

JaCoCo는 `src/main` 제품 코드 전체의 Line Coverage를 측정했다.<br>
커버리지는 90.08%로 중간평가 50%와 빌드 기준 70%를 모두 넘었다.

- [JUnit XML](evidence/req12-154fd09-local/junit/)
- [JaCoCo XML](evidence/req12-154fd09-local/coverage/jacocoTestReport.xml)

## 4. 일반 블록 분포

실제 게임의 `BlockGenerator`를 난이도별로 100,000회 실행했다.<br>
고정 seed는 `20261024`이며, 관측 확률의 상대오차가 기대 확률의 5% 이내인지 검사했다.
Easy의 I는 가중치 12, 나머지는 각각 10이므로 기대 확률은 12/72 = 16.67%다.
이는 요구사항 2의 4쪽에 제시된 가중치 예시를 적용한 값이다.

```text
상대오차(%) = |관측 확률 - 기대 확률| / 기대 확률 × 100
통과 조건 = 상대오차 5% 이하
```

그래프의 검은 마름모는 기대 확률이고 세로 구간은 기대 확률의 95~105%다.<br>
이 구간은 시험의 허용 범위이며 통계적 신뢰구간을 뜻하지 않는다.

난이도별 100,000회 실행은 요구사항의 최소 반복 수 1,000회를 충족한다.

![난이도별 일반 블록 분포](evidence/req12-154fd09-local/block-distribution.png)

모든 21개 조합이 통과했다.<br>
최대 상대오차는 Easy의 Z 블록에서 **1.9736%**였다.

- [원본 CSV](evidence/req12-154fd09-local/distribution/block-distribution.csv)
- [그래프 생성 코드](evidence/req12-154fd09-local/plot.py)

## 5. 아이템 분포

실제 게임의 아이템 생성기를 100,000회 실행했다.<br>
고정 seed는 `20261025`이며, L·O·C·B·P의 기대 확률은 각각 20%다.

그래프의 녹색 구간 19~21%는 기대 확률 20%에 대한 상대오차 5% 허용 범위다.<br>
이 구간도 통계적 신뢰구간이 아니며, 100,000회 실행은 최소 반복 수 1,000회를 충족한다.

![아이템 5종 분포](evidence/req12-154fd09-local/item-distribution.png)

| 아이템 | 관측 횟수 | 관측 확률 | 상대오차 |
|---|---:|---:|---:|
| O | 20,045 | 20.045% | 0.225% |
| B | 20,063 | 20.063% | 0.315% |
| L | 20,015 | 20.015% | 0.075% |
| C | 19,856 | 19.856% | 0.720% |
| P | 20,021 | 20.021% | 0.105% |

다섯 아이템이 모두 상대오차 5% 이내를 충족했다.

- [원본 CSV](evidence/req12-154fd09-local/distribution/item-distribution.csv)
- [그래프 생성 코드](evidence/req12-154fd09-local/plot.py)

## 6. 기능·화면 자동검사

`desktopSmoke`는 실제 JFrame을 열고 합성 입력으로 다음 흐름을 검사했다.

- 시작 메뉴 이동과 반복 입력
- Classic·Item 게임, 정지·재개와 게임 종료
- P 아이템 고정, 300점, 삭제 누계와 10줄 공급
- 설정 저장·재로드와 변경 키 적용
- 게임오버, 모드별 순위 저장·조회·초기화
- 화면 크기 3종과 팔레트 4종의 대표 조합 렌더링

대표 화면은 아래 원본 이미지로 보관했다.

| 범위 | 증빙 |
|---|---|
| Classic · 기본 크기 · Normal | [화면](evidence/req12-154fd09-local/desktop/final-600x500-normal-classic.png) |
| Item · 중간 크기 · Protanopia | [화면](evidence/req12-154fd09-local/desktop/final-750x625-protanopia-item.png) |
| Hard Item · 점수·모드 기록 | [화면](evidence/req12-154fd09-local/desktop/final-hard-item-score.png) |
| P 아이템과 미리보기 | [화면](evidence/req12-154fd09-local/desktop/cross-item-and-preview.png) |

[자동검사 결과 원문](evidence/req12-154fd09-local/desktop/result.txt)은 합성 `KeyListener` 입력의 결과다.<br>
제출 후보의 물리 키보드·마우스 확인은 M02에서 별도로 기록한다.

## 7. 성능 검사

빈 20×10 보드에서 7종 블록의 아래·왼쪽·오른쪽·회전 판정을 총 400,000회 실행했다.<br>
로컬 측정값은 **26ms**이며 팀 기준 5,000ms 이내를 충족했다.

이 결과는 핵심 판정 연산의 회귀 검사다.<br>
실제 장시간 플레이의 응답성과 화면 체감은 최종 배포본 확인 절차에서 다룬다.

## 8. 패키지 결과

`154fd09`에서 새 macOS 실행 ZIP을 생성했다.
아래는 로컬 후보 파일의 기록이며, 최종 게시 파일은 릴리스 본문에서 식별한다.

| 항목 | 결과 |
|---|---|
| 실행 ZIP | `seoultech-tetris-0.1.0-macos-aarch64.zip` |
| 로컬 후보 ZIP SHA-256 | `87a8402eb40931f34052c5880f504c33d96c28c37635bca6dde99c616bb2e301` |
| 구성 | 런처·아이콘·Java 21 runtime 포함 |
| 제품 클래스 | 67개 · 패키지 JAR과 해당 빌드의 클래스가 바이트 단위로 일치 |
| 원본 대조 기록 | [package.json](evidence/req12-154fd09-local/package.json) |

[릴리스 안내](release-notes.md)의 순서로 인수한 파일과 최종 게시 파일의 체크섬을 연결한다.
빌드 환경·서명 등에 따라 ZIP 바이트가 달라질 수 있으므로 파일명이 같다는 이유만으로 같은 제출본으로 취급하지 않는다.

## 9. 제출 후보 인수와 남은 마감 조건

자동검증·분포·커버리지 증빙과 Issue 등록·해결 PR 연결은 완료했다.
코드·문서·증빙을 하나의 PR로 제출하고, 후보 ZIP의 인수 결과도 같은 PR에 반영한다.
팀원 검토와 macOS CI를 확인한 뒤 병합하고 새 tag 릴리스를 게시한다.
태그·소스·빌드·파일·체크섬은 릴리스 본문에 기록하므로 배포 정보만을 위한 후속 문서 PR은 필요 없다.
인수한 ZIP을 그대로 게시하며, 변경한 ZIP에는 이전 결과를 적용하지 않는다.

담당자는 길민규다.
[결과표](evidence/manual-acceptance.csv)에 실제 검증자·날짜·파일·SHA-256·관찰 결과를 기록한다.
현재 기기는 macOS 26.6.2, MacBookPro17,1, Apple M1, RAM 8GiB이며 기록 당시 여유 공간은 약 28.4GiB다.
[기기 정보](evidence/manual-environment-20261026.json)에 조회값을 보관했다.
CPU 주파수는 이 Mac의 시스템 조회에 노출되지 않아, 원문의 1.2GHz 조건과의 대응은 M08에서 확인한다.

아래는 팀이 정한 인수 절차다.
관찰·판정은 실제 수행 후 기록한다.

| ID | 확인할 내용 | 통과 기준 | 연결 요구사항 |
|---|---|---|---|
| M01 | ZIP 압축 해제·앱 더블클릭 | 별도 Java·명령 없이 시작 메뉴 표시 | D03·D04·I11, R2-P35·36 |
| M02 | 두 모드의 물리 입력·클릭 후 포커스 | 조작 가능한 상태에서 좌우 교대 20회, 회전·낙하·정지/재개·메뉴 입력이 의도대로 반영 | N04·I07, R2-N06 |
| M03 | 크기·키·색상·난이도 저장 후 완전 종료·재실행 | 저장한 값 복원, 변경 키로 조작 | N09·I07, R2-N08 |
| M04 | 모드별 기록 저장 후 완전 종료·재실행 | All·Classic·Item의 이름·점수·난이도·모드 유지 | N10·I11, R2-N08 |
| M05 | 4팔레트에서 일반 블록·5종 아이템 식별 | J/L·S/Z 등 일반 블록과 L·O·C·B·P를 모양·문자·색으로 구분, 효과·점수는 명세와 일치 | N06~N08, R2-N05·P42 |
| M06 | Classic·Item 각각 5분 플레이, 3크기 전환 | 필수 정보 가림·조작 가능 상태의 입력 누락·플레이 중단 지연 없음, 오류는 재현 절차와 함께 기록 | N14·E07, R2-N10 |
| M07 | 설정·순위 초기화 후 완전 종료·재실행 | 설정 기본값과 빈 순위 유지, 두 초기화의 독립성 유지 | I09·E10 |
| M08 | 실제 인수 기기 사양 대조 | OS·CPU 기준·RAM 1GB 이상·여유 디스크 500MB 이상 확인 | N13·E08, R2-N10 |

M05는 팔레트 이미지 생성과 별도로 사용자 구분성을 확인하는 절차다.
색각 유형별 판별에 사용한 방법·대상 조합·관찰 결과도 함께 남긴다.

단일 PR에는 코드·테스트·정본 8개·[204개 증빙 색인](evidence/requirements-status-20261026.csv)·Issue #23~29·인수 결과를 포함한다.
[릴리스 안내](release-notes.md)에 게시할 파일과 버전 연결 순서를 정리했다.
Windows CI [Issue #29](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/29)는 제출 대상 외 환경의 후속 작업으로 추적한다.

## 10. 증빙 원본

| 자료 | 위치 |
|---|---|
| 검증 결과·파일 해시 | [`summary.json`](evidence/req12-154fd09-local/summary.json) |
| 일반·아이템 분포 CSV | [`distribution/`](evidence/req12-154fd09-local/distribution/) |
| JUnit XML 22개 | [`junit/`](evidence/req12-154fd09-local/junit/) |
| JaCoCo XML | [`coverage/jacocoTestReport.xml`](evidence/req12-154fd09-local/coverage/jacocoTestReport.xml) |
| GUI 결과·대표 이미지 | [`desktop/`](evidence/req12-154fd09-local/desktop/) |
| 소스·실행·패키지 기록 | [`source.json`](evidence/req12-154fd09-local/source.json) · [`build.log`](evidence/req12-154fd09-local/build.log) · [`package.json`](evidence/req12-154fd09-local/package.json) |
| 분포 그래프 재생성 | [`plot.py`](evidence/req12-154fd09-local/plot.py) |
| 원본 해시 목록 | [`files.json`](evidence/req12-154fd09-local/files.json) |
| 원본 대조 검사 | [`verify.py`](evidence/req12-154fd09-local/verify.py) |

그래프와 요약 파일은 저장소 루트에서 다음 명령으로 재생성·검증한다.<br>
Matplotlib은 이 작업에서만 내려받으며 프로젝트 의존성에는 추가하지 않는다.

```bash
uv run --with matplotlib==3.11.2 python docs/evidence/req12-154fd09-local/plot.py
python3 docs/evidence/req12-154fd09-local/verify.py
```

`verify.py`는 보관한 XML·CSV의 수치와 파일 해시, 검증 커밋의 소스 해시를 다시 대조한다.
수동 인수 결과표는 빌드별 자동검증 자료와 분리하여 관리한다.

## 관련 문서

- [요구사항 1 체크리스트](requirements-1-checklist.md)
- [요구사항 2 체크리스트](requirements-2-checklist.md)
- [게임 명세서](specification.md)
- [사용자 안내서](user-guide.md)
- [개발 안내서](development.md)
- [프로젝트 관리 기록](project-management.md)
