# 중간평가 릴리스 안내

공식 제출 환경은 **macOS 26 이상**이다.
실행 앱에는 Java 21 runtime과 아이콘을 포함한다.

## 이전 공개 버전 · 2026-10-26 확인

| 항목 | 값 |
|---|---|
| 공개 후보 | [v0.1.0-req2.2](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.2) · prerelease |
| 소스 | main `62a035f` · 포커스 수정 PR #22 포함 |
| 실행 ZIP | `seoultech-tetris-0.1.0-macos-aarch64.zip` |
| ZIP SHA-256 | `520970449586b8693325d1fedfb3b9d723601189cdd28233f8c7b82a3e8ce23a` |
| 검증 | [이전 macOS CI 원본](evidence/req12-e19f900/summary.json) |
| 문서 | 현재 tag의 문서이며 이번 최종 업데이트는 미포함 |

이 공개 후보는 `62a035f`를 가리킨다.<br>
B/C 효과 뒤 셀 정착과 O 색상 조정은 포함하지 않는다.

## 이번 제출에 포함하는 변경

| 항목 | 값 |
|---|---|
| 기준 | 팀 `main` `62a035f` |
| 작업 커밋 | `154fd097deff2675e7030f607b0ceeb35b9e69c5` |
| B/C 효과 | 삭제 뒤 열마다 남은 셀을 아래로 정착하며 순서·색·문자 유지 |
| O 색상 | 기존 회색보다 밝게 조정 |
| P 아이템 | 기존 행·열 삭제, 점수·누계 정책 유지 |
| 로컬 JUnit | 98개 통과 · 실패 0 · 오류 0 |
| 로컬 Line Coverage | 1,534/1,703 = 90.08% |

위 수치는 `154fd09`의 로컬 자동검증 결과다.
GUI 검사와 macOS 패키징도 통과했으며, 원본은 [검증 보고서](verification.md)에 연결했다.
PR의 CI 결과와 최종 배포 파일은 해당 릴리스 본문에서 확인한다.

## 단일 PR과 제출 릴리스

코드·테스트·정본 문서·증빙을 하나의 PR에서 완성한다.

1. PR의 macOS CI에서 제출 후보 ZIP과 체크섬을 생성한다.
2. CI에서 받은 동일 ZIP으로 수동 인수 M01~M08을 수행한다.
3. 실제 환경·파일·SHA-256·관찰 결과를 같은 PR의 검증 자료에 반영한다.
4. 작성자 외 팀원 1명이 최종 변경과 인수 결과를 검토한다.
5. macOS CI 통과를 다시 확인한 뒤 PR을 `main`에 병합한다.
6. 병합 커밋에 새 tag를 만들고, 인수한 동일 ZIP과 체크섬을 릴리스에 첨부한다.

인수 뒤 제품 코드나 ZIP이 바뀌면 새 파일로 M01~M08을 다시 수행한다.<br>
인수 결과를 기록하는 문서만 바뀐 경우에도 최종 tag와 인수 ZIP의 제품 코드가 같은지 확인한다.

릴리스 뒤 별도 최종 문서 PR은 만들지 않는다.<br>
정본 문서는 tag의 README와 [GitHub Releases](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases)에서 연결한다.

### 릴리스 본문에 기록할 값

| 항목 | 기록 원칙 |
|---|---|
| tag·소스 | 실제 tag와 tag가 가리키는 병합 커밋 SHA |
| 빌드 | 인수 ZIP을 만든 정확한 커밋 SHA와 macOS CI URL |
| 실행 파일 | 실제 첨부한 ZIP 파일명과 SHA-256 |
| 검증 | 같은 ZIP의 M01~M08 결과와 검증 보고서 링크 |
| 문서 | tag에 포함된 README·정본 문서 링크 |

문서의 다운로드 링크는 Releases 페이지로 유지하고, 위 값은 배포 시 릴리스 본문에 확정한다.

## 릴리스에 제공할 자료

| 자료 | 제공 방식 |
|---|---|
| macOS 실행 ZIP | Release asset |
| 실행 ZIP의 SHA-256 | Release asset |
| 실행 안내 | 릴리스 본문과 해당 tag의 사용자 안내서 |
| 요구사항·명세·검증·프로젝트 관리 | 해당 tag의 README에서 정본 8개로 연결 |
| 원본 CSV·그래프·JUnit·커버리지·인수 결과 | 해당 tag의 `docs/evidence/` |
| 전체 소스와 문서 | GitHub가 제공하는 해당 tag의 Source code ZIP |

현재 패키징 설정의 파일명 버전은 `0.1.0`이다.
정확한 제출 파일은 tag·생성 커밋·체크섬으로 구분한다.

## 포함 기능

- Classic·Item 모드, Easy·Normal·Hard 난이도
- 7종 테트로미노, 이동·회전·하드드롭·줄 삭제·점수·레벨
- L·O·C·B·P 5종 아이템과 10줄 단위 누적 공급
- 모드별 순위와 난이도·모드 기록, 설정·기록 저장 및 초기화
- 화면 크기 3종, 조작키 변경, 색상 모드 4종
- 점수창·MENU·랭킹 스크롤바 조작 뒤 키보드 입력 유지

## 관련 문서

- [제출 안내](../README.md)
- [검증 보고서](verification.md)
- [프로젝트 관리 기록](project-management.md)
- [개발 안내서](development.md)
