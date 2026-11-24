# P2P TCP 통신 기반 구현 및 내부 IP 검증 보고서

작성: 2026-11-23 · 기준 커밋: `d391d07` (`main`)

Requirement 3(P2P 네트워크 대전)의 통신 기반을 기존 게임 로직(`GameEngine`, `Board`, UI)과 분리된 독립 모듈(`seoultech.se.tetris.network`)로 구현하고, JUnit 단위 테스트 및 **실제 내부 IP + 포트 `30000` 기반 실통신 검증**을 완료한 기록이다.

---

현재 게임 화면과 메시지 흐름은 [P2P 대전 연결과 경기 동기화](p2p-network-gui-specification.md)에 정리했다. 아래는 11월 23일 통신 모듈 단독 검증 기록이다.

## 1. 구현 구성 (`seoultech.se.tetris.network`)

기존 게임 파일은 수정하지 않았으며, 실제 게임 화면(`Networkpanel`, `Multigamepanel`)에서는 `P2PNetworkManager` 객체를 통해 통신 상태 판별과 게임 데이터 송수신을 수행한다.

| 파일 | 역할 |
|---|---|
| [P2PNetworkManager.java](../src/main/java/seoultech/se/tetris/network/P2PNetworkManager.java) | 단일 스레드 비차단(Non-blocking) TCP P2P 통신 매니저 (`ServerSocketChannel` / `SocketChannel`, `TCP_NODELAY=true`) |
| [P2PPacket.java](../src/main/java/seoultech/se/tetris/network/P2PPacket.java) | 비암호화 JSON 직렬화/역직렬화, 비트 플래그 상태 관리, 수신부 엄격 Validation |
| [P2PConfig.java](../src/main/java/seoultech/se/tetris/network/P2PConfig.java) | 폴링(17ms), 하트비트(500ms), 정지(999ms), 종료(10,000ms), 소켓 타임아웃(1ms) 설정값 (조정 가능) |
| [P2PListener.java](../src/main/java/seoultech/se/tetris/network/P2PListener.java) | 연결, 패킷·시드·모드·동의 수신, 일시정지/재개/종료 요청, Validation 오류, RTT 갱신 콜백 |
| [P2PConnectionState.java](../src/main/java/seoultech/se/tetris/network/P2PConnectionState.java) | 통신 상태 (`DISCONNECTED`, `LISTENING`, `CONNECTED`, `PAUSED`) |
| [P2PDisconnectReason.java](../src/main/java/seoultech/se/tetris/network/P2PDisconnectReason.java) | 연결 종료 사유 (`LOCAL_CLOSED`, `PEER_DISCONNECTED`, `TIMEOUT`, `IO_ERROR`) |

### 주요 외부 API

- **서버/클라이언트 연결**: `startServer(int port)`, `startServer(String bindHost, int port)`, `acceptPendingConnection()`, `connect(String host, int port)`, `resolveLocalInternalIp()`, `getLocalAddress()`, `getRemoteAddress()`
- **로비 및 게임 송수신**: `sendModeProposal(String mode)`, `sendModeAgreement(boolean agreed)`, `sendSeed(long seed)`, `sendGameplayInput(int keyCode, int stateFlags)`, `sendHeartbeatPing()`
- **단일 스레드 폴링 및 상태 조회**: `poll()`, `pollReceivedPacket()`, `drainReceivedPackets()`, `isConnected()`, `isPaused()`, `isActive()`, `getRttMillis()`, `getSilenceMillis()`, `close()`

---

## 2. 요구사항 반영 내역

| 요구사항 | 구현 내용 |
|---|---|
| TCP 기반 신뢰 통신 | `ServerSocketChannel` / `SocketChannel`, `TCP_NODELAY=true`, `SO_REUSEADDR=true` |
| 1ms 이하 응답 / 단일 스레드 60Hz 폴링 | Non-blocking 채널 + `socketTimeoutMs=1`, 기본 `pollIntervalMs=17` (60Hz) |
| 패킷 구성 및 비트 플래그 | `{"KeyCode":int,"State":int,"Client/Server_time":float}` 형식. `STATE_NONE`, `STATE_CURR_CHANGED`, `STATE_LEVEL_UP`, `STATE_GAME_OVER`, `STATE_HEARTBEAT_PING/PONG`, `STATE_SEED_SYNC`, `STATE_MODE_PROPOSE/AGREE`, `STATE_PAUSE/RESUME`, `STATE_DISCONNECT` 비트 플래그 조합 지원 |
| 비암호화 + 수신부 Validation | 평문 JSON 전송. 수신 시 null/공백, 깨진 JSON, 필수 필드 누락, 알 수 없는/중복 키, 미정의 비트 플래그, 음수/NaN 타임스탬프, 4,096B 초과 패킷을 거부하고 연결 유지 |
| 500ms 하트비트 & 양방향 RTT | 500ms 유휴 시 빈 RTT 측정 패킷(`STATE_HEARTBEAT_PING`) 자동 전송. 게임 입력 패킷 전송 시 빈 패킷 타이머 초기화. 상대방이 `Client/Server_time`을 `STATE_HEARTBEAT_PONG`으로 에코하여 서버·클라이언트 양측에서 RTT(ms) 계산 |
| Exception Flow (999ms / 10,000ms) | 마지막 유효 패킷 수신 후 **999ms 이상** 경과 시 즉시 `PAUSED` 전환 및 `onPauseRequested()` 호출 → **10,000ms 이내** 수신 시 `CONNECTED` 복귀 및 `onResumeRequested()` 호출 → **10,000ms 이상** 미수신 시 `DISCONNECTED` (`TIMEOUT`) 전환 및 `onDisconnectRequested(TIMEOUT)` 호출. 임계값은 `P2PConfig`로 조정 가능 |

---

## 3. 테스트 구성 및 검증 결과

### 3.1 신규 JUnit 테스트 파일

1. [P2PNetworkManagerTest.java](../src/test/java/seoultech/se/tetris/network/P2PNetworkManagerTest.java) — **9개 테스트 통과**
   - 기본/커스텀 `P2PConfig` 임계값 검증
   - `P2PPacket` 직렬화·비트 플래그 조합 및 비정상 페이로드 Validation 검증 (실제 루프백 소켓 손상 패킷 주입 포함)
   - Normal Flow: 서버/클라이언트 연결 → 모드 제안/동의 → 양측 시드 공유 → 양방향 게임 입력 FIFO 송수신
   - 하트비트 타이머 리셋 및 서버·클라이언트 양측 결정론적 RTT 계산 검증
   - Exception Flow: 998ms 유지 → 999ms 정지 → 10,000ms 이내 재개 / 10,000ms 도달 시 타임아웃 종료 / 커스텀 임계값 검증
   - 정상 종료(`close()`) 상대방 감지 및 빈 소켓 비차단 `poll()` 즉시 반환 확인
2. [P2PInternalIpCommunicationTest.java](../src/test/java/seoultech/se/tetris/network/P2PInternalIpCommunicationTest.java) — **2개 테스트 통과**
   - 모의 시계가 아닌 **실제 시스템 클럭(`System::nanoTime`)과 실제 내부 IP + 고정 포트 `30000`** 사용
   - `twoPeersCommunicateOverInternalIpOnPort30000`:
     - `resolveLocalInternalIp()`로 탐색한 내부 IP(`192.168.35.211`)와 포트 `30000`에 서버 명시 바인딩 및 클라이언트 접속
     - 모드 제안/동의, 양측 시드 교환(`111222333L`, `999888777L`)
     - 서버·클라이언트 각 20개씩 양방향 게임 입력 패킷 연속 송수신 (FIFO 순서 및 데이터 100% 일치 확인)
     - 실제 소켓 왕복 RTT 양방향 측정 확인
   - `internalIpPort30000HandlesValidationPauseResumeAndTimeoutExceptions`:
     - 동일 내부 IP + 포트 `30000`에서 손상 패킷 Validation, 실제 시간 기반 Pause → Resume 복구, Timeout Disconnect 검증

### 3.2 실측 실행 결과

```text
[P2P Internal IP OK] 192.168.35.211:30000 | packets=20 each way | serverRTT=7.442ms | clientRTT=0.676ms
```

| 항목 | 결과 |
|---|---|
| `P2PNetworkManagerTest` | 9개 통과 (실패·건너뜀 0) |
| `P2PInternalIpCommunicationTest` | 2개 통과 (실패·건너뜀 0) |
| 기존 프로젝트 전체 회귀 테스트 | 전부 통과 (기존 게임 파일 무변경) |
| 전체 Line Coverage (JaCoCo) | **2,309 / 2,611 = 88.43%** (기준 70% 통과) |
| `seoultech.se.tetris.network` Line Coverage | **415 / 501 = 82.83%** |
| 신규 코드 컴파일 경고 (`-Xlint:all`, `--release 21`) | **0건** |
| Gradle 검증 (`check build`) | `BUILD SUCCESSFUL` |

---

## 4. 다음 단계 (물리 환경 NAT / 외부 IP 테스트)

내부 IP(`192.168.35.211:30000`) 기반 두 `P2PNetworkManager` 객체 간 실질적인 TCP 송수신, 핸드셰이크, 양방향 RTT 측정, 예외처리가 모두 검증되었다. 이후 물리 환경에서 다음 순서로 외부 IP / NAT 통신 테스트를 진행할 수 있다.

1. 서버 측 공유기에서 TCP 포트 `30000` 포트포워딩(NAT) 설정 후 `startServer(30000)` 실행
2. 클라이언트 측에서 서버의 외부 IP와 포트 `30000`으로 `connect(publicIp, 30000)` 호출
3. 실제 게임(`Networkpanel`, `Multigamepanel`) 연동 시 `P2PListener` 콜백을 통해 일시정지/재개/종료 및 상대방 보드 입력을 연결
