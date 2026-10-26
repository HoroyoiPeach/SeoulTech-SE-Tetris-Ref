# 프로젝트 관리 기록

이 문서는 요구사항 1·2의 업무 분담, 버전 관리, PR, 이슈 및 주요 결정을 한곳에서 확인하기 위한 기록이다.

- 저장소: [HoroyoiPeach/SeoulTech-SE-Tetris-Ref](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref)
- 진척 관리: [GitHub Project](https://github.com/users/HoroyoiPeach/projects/3/views/1)
- 제품 기준: `154fd09` · 팀 `main` `62a035f`에서 후속 아이템 동작 개선
- 제출 대상: macOS 26 이상
- 최종 tag와 macOS ZIP: 다음 릴리스에서 배포

## 1. 팀원별 수행 내역

역할은 Git 커밋과 PR에서 직접 확인되는 결과물만 정리했다.

| 팀원 | GitHub | 확인된 수행 내역 | 대표 근거 |
|---|---|---|---|
| 최지원 | [HoroyoiPeach](https://github.com/HoroyoiPeach) | 설정·시작 화면 구성, 하드드롭과 키 설정, 저장소 정리, PR 병합 | [PR #8](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/8), [커밋 이력](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/commits/main/?author=HoroyoiPeach) |
| 최승빈 | [Choiseungbin7391](https://github.com/Choiseungbin7391) | 기본 게임·점수판·설정·색각 모드·게임오버·난이도·아이템 구현, UI 리팩터링, PR 병합 | [PR #4](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/4), [#6](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/6), [#9](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/9), [#10](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/10), [#15](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/15) |
| 길민규 | [CODEhenryKIL](https://github.com/CODEhenryKIL) | 요구사항·칸반 정리, 오류 수정, 구조 개선, Gradle·JUnit·CI·패키징, 요구사항 2 보완 구현과 통합 검증·문서화 | [PR #1](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/1), [#5](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/5), [#11–14](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pulls?q=is%3Apr+is%3Aclosed+author%3ACODEhenryKIL), [#16–22](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pulls?q=is%3Apr+author%3ACODEhenryKIL) |

## 2. 개발·검토 흐름

앞으로의 변경은 다음 순서로 처리한다.

1. 칸반 카드에 목적·담당·완료 조건을 기록한다.
2. 작업별 브랜치에서 작은 단위로 커밋한다.
3. `main` 대상 PR에 변경 이유와 검증 결과를 작성한다.
4. macOS CI 통과를 확인한다.
5. 작성자 외 팀원 1명이 검토한 뒤 병합한다.
6. 병합 결과와 관련 카드·Issue 상태를 갱신한다.

브랜치 이름과 상세 작업 규칙은 [CONTRIBUTING](../CONTRIBUTING.md)을 따른다.

일반 기능·문서 작업은 칸반으로 관리한다.<br>
실제 버그와 CI 검증 실패는 GitHub Issue로 등록하고 해결 PR을 연결한다.

적용 기준일은 2026-10-26이다.

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

PR #15는 구현을 보존한 뒤 확인된 오류를 별도 브랜치에서 고치는 방식으로 처리했다.<br>
여섯 오류의 원인·수정·검증은 [PR #17](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/pull/17)에 연결되어 있다.

### 중간평가 제출 변경

현재 작업 커밋은 `154fd097deff2675e7030f607b0ceeb35b9e69c5`이며 팀 `main`의 `62a035f`에서 시작했다.

- B 효과 뒤 남은 셀을 열마다 아래로 정착한다.
- C 효과 뒤 남은 셀을 열마다 아래로 정착한다.
- 정착 과정에서 셀의 순서·색·문자를 유지한다.
- O 색상을 기존 회색보다 밝게 조정한다.
- P의 기존 효과·점수·누계 정책은 유지한다.

로컬 `build`에서 JUnit 98개가 실패·오류 없이 통과했다.<br>
Line Coverage는 1,534/1,703 = 90.08%다.

GUI 자동검사와 macOS 패키징도 통과했다.
[검증 보고서](verification.md)에 로컬 원본을 연결하고, 실제 PR·CI·배포 정보는 릴리스 본문에 기록한다.

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
| 릴리스 관리 | 소스·CI·패키지·체크섬을 같은 버전으로 연결 | [개발 안내서](development.md#13-제출-버전-고정) |

## 7. 단일 PR과 최종 릴리스

현재 작업은 코드·테스트·정본 문서·증빙을 하나의 PR로 제출한다.

1. PR의 macOS CI에서 제출 후보 ZIP과 체크섬을 생성한다.
2. 생성된 동일 ZIP으로 macOS 수동 인수 M01~M08을 수행한다.
3. 실제 환경·파일·SHA-256·관찰 결과를 같은 PR의 검증 자료에 반영한다.
4. 작성자 외 팀원 1명이 최종 변경과 결과를 검토한다.
5. CI 통과와 인수 결과를 확인한 뒤 `main`에 병합한다.
6. 병합 커밋에 새 tag를 만들고, 인수한 동일 ZIP과 체크섬을 릴리스에 첨부한다.

인수 뒤 제품 코드나 ZIP이 바뀌면 새 파일로 M01~M08을 다시 수행한다.<br>
문서·증빙만 추가한 경우에도 인수 ZIP의 제품 코드와 최종 tag의 제품 코드가 같은지 확인한다.

릴리스 본문에는 실제 tag, tag의 소스 커밋, ZIP을 만든 커밋과 CI, 파일명, SHA-256을 기록한다.<br>
문서는 [GitHub Releases](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases)와 tag에 포함된 README에서 확인한다.

이 릴리스 이후 별도 최종 문서 PR은 만들지 않는다.

현재 공개 후보는 [v0.1.0-req2.2](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.2)다.
이 후보는 `62a035f`를 가리키므로 위 B/C 정착과 O 색상 조정을 포함하지 않는다.
세부 순서는 [릴리스 안내](release-notes.md)를 따른다.

## 관련 문서

- [요구사항 1 체크리스트](requirements-1-checklist.md)
- [요구사항 2 체크리스트](requirements-2-checklist.md)
- [게임 명세서](specification.md)
- [사용자 안내서](user-guide.md)
- [개발 안내서](development.md)
- [검증 보고서](verification.md)
