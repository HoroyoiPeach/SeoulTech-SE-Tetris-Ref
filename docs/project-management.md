# 프로젝트 관리 기록

이 문서는 요구사항 1·2의 업무 분담, 작업 규칙, PR, Issue와 주요 결정을 한곳에서 관리한다.

- 저장소: [HoroyoiPeach/SeoulTech-SE-Tetris-Ref](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref)
- 진척 관리: [GitHub Project](https://github.com/users/HoroyoiPeach/projects/3/views/1)
- 공개 릴리스: [`v0.1.0-req2.3`](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.3)
- 제출 소스: `10d7b78` · 제품 코드 `154fd09`
- 제출 대상: macOS 26 이상 Apple Silicon

## 1. 팀원별 수행 내역

역할은 Git 커밋과 PR에서 직접 확인되는 결과물만 정리했다.

| 팀원 | GitHub | 확인된 수행 내역 | 대표 근거 |
|---|---|---|---|
| 최지원 | [HoroyoiPeach](https://github.com/HoroyoiPeach) | 설정·시작 화면 구성, 하드드롭과 키 설정, 저장소 정리, PR 병합 | [PR #8](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/8), [커밋 이력](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/commits/main/?author=HoroyoiPeach) |
| 최승빈 | [Choiseungbin7391](https://github.com/Choiseungbin7391) | 기본 게임·점수판·설정·색각 모드·게임오버·난이도·아이템 구현, UI 리팩터링, PR 병합 | [PR #4](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/4), [#6](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/6), [#9](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/9), [#10](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/10), [#15](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/15) |
| 길민규 | [CODEhenryKIL](https://github.com/CODEhenryKIL) | 요구사항·칸반 정리, 오류 수정, 구조 개선, Gradle·JUnit·CI·패키징, 요구사항 2 보완 구현과 통합 검증·문서화 | [PR #1](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/1), [#5](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/5), [#11–14](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pulls?q=is%3Apr+is%3Aclosed+author%3ACODEhenryKIL), [#16–22](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pulls?q=is%3Apr+author%3ACODEhenryKIL) |

## 2. 개발·검토 흐름

일반 기능·문서 작업은 칸반으로 관리한다.
실제 버그와 CI 검증 실패는 GitHub Issue로 등록하고 해결 PR을 연결한다.

1. 카드에 목적·담당·완료 조건을 기록하고 `In Progress`로 옮긴다.
2. 최신 `main`에서 `feature/`, `fix/`, `docs/` 브랜치를 만든다.
3. 한 브랜치에서 한 작업만 진행하고 이해 가능한 작은 단위로 커밋한다.
4. 직접 실행과 관련 테스트를 마친 뒤 `main` 대상 PR에 변경 이유와 결과를 기록한다.
5. 카드를 `Review`로 옮기고 제출 대상 macOS의 CI 결과를 확인한다.
6. 작성자 외 팀원 1명이 기능·회귀·코드·검증 결과를 확인한 뒤 병합한다.
7. 병합 뒤 카드와 Issue를 갱신하고 완료 작업을 `Done`으로 옮긴다.

`main`에는 일반 작업을 직접 push하지 않는다.
충돌이 나면 어느 한쪽 변경을 임의로 버리지 않고 작성자와 범위를 확인한 뒤 두 기능을 다시 검증한다.
중요한 합의는 대화에만 남기지 않고 카드나 이 문서에 기록한다.

이 규칙은 2026-10-26부터 적용한다.
과거 모든 PR이 동일한 검토 절차를 거쳤다는 의미는 아니다.

## 3. 주요 변경 이력

| 단계 | PR | 결과 |
|---|---|---|
| 관리 기반 | [#1](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/1), [#2](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/2) | 작업 규칙·GitHub 양식과 요구사항 추적표 마련 |
| 요구사항 1 기본 기능 | [#4](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/4), [#6](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/6), [#8](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/8), [#9](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/9) | 게임 진행·설정·하드드롭·점수·게임오버·색각 모드 구현 |
| 오류 수정 | [#5](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/5), [#7](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/7) | 회전·게임오버·점수 파일·난수 및 설정 입력·복구 오류 수정 |
| 구조 개선 | [#10](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/10), [#11](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/11), [#12](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/12), [#13](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/13), [#14](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/14) | 설정·화면·보드 진행·충돌·점수 저장·미리보기 책임 정리 |
| 요구사항 2 초안 | [#15](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/15) | 난이도·애니메이션·아이템 모드와 초기 아이템 구현 |
| 자동화·배포 기반 | [#16](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/16) | Java 21·Gradle·JUnit·JaCoCo·CI·실행 ZIP 패키징 통합 |
| 요구사항 2 보완 | [#17](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/17), [#18](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/18), [#19](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/19), [#20](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/20) | PR #15 오류 수정, 기존 기능 수용 검사, 모드별 기록, 5종 아이템과 누적 공급 완성 |
| 통합 검증 | [#21](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/21) | 요구사항 1·2 회귀, 설정·기록 유지, 화면 조합과 평가 증빙 통합 |
| 입력 안정화 | [#22](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/22) | 클릭 뒤 키보드 포커스 유지와 관련 회귀 검사 추가 |
| 중간평가 제출 | [#30](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/30) | B·C 정착, O 색상 조정, 정본 문서와 제출 증빙을 `10d7b78`에 통합 |

PR #15는 구현을 보존한 뒤 확인된 오류를 별도 브랜치에서 고치는 방식으로 처리했다.<br>
여섯 오류의 원인·수정·검증은 [PR #17](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/17)에 연결되어 있다.

### 중간평가 제출 결과

PR #30을 `main`의 `10d7b78`로 병합하고 `v0.1.0-req2.3`을 공개했다.
실행용 ZIP과 소스·문서·증빙을 묶은 제출용 ZIP을 같은 릴리스에서 제공한다.

제품 코드는 B·C 효과 뒤 열별 정착, 셀 순서·색·문자 보존과 밝은 회색 O 표시를 포함한다.
P의 행·열 삭제와 점수·누계 정책은 유지했다.

검증 수치와 원본은 [검증 보고서](verification.md)에서 관리한다.
배포 파일명과 SHA-256은 [개발 안내서](development.md#13-버전과-릴리스-식별)와 릴리스 본문에서 확인한다.

## 4. Issue와 칸반

GitHub Project에는 요구사항별 작업과 진척을 관리한다.<br>
PR에는 카드·선행 작업·검증 결과를 연결한다.

2026-10-26 기준 Issue 7개에 증상·수정·검증 결과를 연결했다.<br>
해결된 6개는 Closed, 별도 CI 추적 1개는 Open 상태다.

| Issue | 결함 | 해결 PR | 상태 |
|---|---|---|---|
| [#23](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/23) | 회전·게임오버·점수 파일·난수 반복 | [#5](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/5) | Closed |
| [#24](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/24) | 설정 키 충돌·손상 파일 복구 | [#7](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/7) | Closed |
| [#25](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/25) | 키 변경·초기화·창 크기 회귀 | [#11](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/11) | Closed |
| [#26](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/26) | PR #15의 난이도·아이템 오류 6건 | [#17](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/17) | Closed |
| [#27](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/27) | 복수 아이템 공급 누락·B 위치 이동 | [#20](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/20) | Closed |
| [#28](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/28) | 클릭 후 키보드 입력 중단 | [#22](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/22) | Closed |
| [#29](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/issues/29) | 제출 대상 외 Windows CI 크기 검사 | 원인 조사·재검증 | Open |

상태·등록일·본문은 [Issue 기록](evidence/project-issues-20261026.json)으로 보관했다.

## 5. 주요 결정

| 주제 | 결정 | 확인 위치 |
|---|---|---|
| 제출 환경 | macOS 26 이상, Java 21 runtime 동봉 | [README](../README.md) |
| 난이도 | Easy·Normal·Hard, 초기 1초 유지 후 속도 증가 폭에 0.8·1.0·1.2 적용 | [게임 명세서](specification.md#23-낙하-속도) |
| 확률 검사 | 실제 생성기, 고정 seed, 100,000회, 상대오차 5% | [검증 보고서](verification.md) |
| 아이템 공급 | 삭제 누계 10줄마다 1개, 여러 경계 통과 시 권리 보존·순차 공급 | [게임 명세서](specification.md#7-아이템-공급-공통-정책) |
| 아이템 선택 | L·O·C·B·P 각각 20% | [게임 명세서](specification.md#7-아이템-공급-공통-정책) |
| P 아이템 | 2×2 블록이 고정되면 해당 두 행·두 열 삭제, 가로 두 행 300점·2줄 누계 | [게임 명세서](specification.md) |
| 순위 | All·Classic·Item 조회, 난이도·모드 저장, 구형 기록은 All에서 유지 | [게임 명세서](specification.md#6-순위와-기록) |

별도의 교수·e-class 답변이나 외부 합의는 없었다.<br>
새로운 모호성이 생기면 구현 전에 e-class 문의 필요성을 검토한다.

## 6. 수업 내용 적용

| 개념 | 적용 내용 | 근거 |
|---|---|---|
| 요구사항 분석·추적 | 명시·도출·결정 요구사항을 체크리스트와 명세에 연결 | [요구사항 1](requirements-1-checklist.md), [요구사항 2](requirements-2-checklist.md) |
| 형상 관리 | 기능·오류·리팩터링을 브랜치와 PR 단위로 분리 | [Pull Requests](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pulls?q=is%3Apr) |
| 회귀 테스트·CI | JUnit·JaCoCo·분포·성능·GUI 검사를 Gradle과 CI로 반복 실행 | [개발 안내서](development.md), [검증 보고서](verification.md) |
| 리팩터링 | 중복 흐름과 책임을 단계별 PR로 정리하고 회귀 검사로 동작 보존 | [PR #11–14](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pulls?q=is%3Apr+is%3Aclosed+author%3ACODEhenryKIL) |
| 릴리스 관리 | 소스·CI·패키지·체크섬을 같은 버전으로 연결 | [개발 안내서](development.md#13-버전과-릴리스-식별) |

## 7. 릴리스와 문서 정본 관리

릴리스에는 tag, 소스 커밋, 빌드 기준, 대상 OS, 실행 파일명과 SHA-256, 검증 결과와 정본 문서 링크를 기록한다.
제품 코드나 배포 ZIP이 바뀌면 변경된 파일로 자동검사와 수동 인수를 다시 수행한다.

현재 공개 제출본은 `v0.1.0-req2.3`이다.
병합 PR은 #30, 제출 소스는 `10d7b78`, 제품 코드는 `154fd09`다.

현재 안내는 README와 `docs/`의 정본 7개만 유지한다.
완료된 계획·임시 점검표·과거 논의는 Git 이력에서 확인하며 현재 기준으로 사용하지 않는다.
자동검증 원본과 평가 증빙은 `docs/evidence/`에 유지한다.

### 2026-10-27 문서 정리 예외

사용자가 정본 8개만 남기도록 요청한 문서 정리는 PR 없이 `main`에 직접 반영한다.
범위는 기존 문서의 로컬 백업, 중복·과거 문서 삭제, 정본 링크와 현재 사실 정리에 한정한다.
제품 코드·빌드·CI 설정은 변경하지 않는다.

이 항목은 이번 문서 정리에만 적용되는 예외다.
이후 작업은 작성자 외 팀원 1명 검토와 PR 원칙을 다시 따른다.

## 관련 문서

- [요구사항 1 체크리스트](requirements-1-checklist.md)
- [요구사항 2 체크리스트](requirements-2-checklist.md)
- [게임 명세서](specification.md)
- [사용자 안내서](user-guide.md)
- [개발 안내서](development.md)
- [검증 보고서](verification.md)
