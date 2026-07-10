# snake-game — slither.io 스타일 실시간 멀티플레이 지렁이 게임

## 빌드·실행
- 빌드(테스트 포함): `.\gradlew.bat build`
- 테스트만: `.\gradlew.bat test`
- 서버 기동: `.\gradlew.bat bootRun` → http://localhost:8085 접속
  (로컬 8080/8081/8082/8090 점유로 `server.port=8085` 사용 — application.properties)

## 스택
- Java 21 / Spring Boot 3.5 / Gradle Wrapper (Gradle 별도 설치 불필요)
- 순수 WebSocket(TextWebSocketHandler + JSON) — STOMP 미사용
- 클라이언트: 순수 HTML5 Canvas + Vanilla JS (React 금지)

## 구조
```
src/main/java/com/rathon/snakegame/
├── game/       # 순수 게임 로직 — WebSocket과 분리, 단위 테스트 대상
│   ├── GameConfig.java   # 맵·틱·속도·충돌 상수
│   ├── Vec2.java         # 불변 2D 벡터
│   ├── Snake.java        # 이동·회전 제한·성장·부스트 소모
│   └── GameWorld.java    # 틱 진행·섭취·충돌·사망 변환·리더보드·먹이 증분 추적
├── protocol/   # JSON 메시지 record (joined / state / dead, SnakeDto / FoodDto)
├── ws/         # WebSocket 계층
│   ├── GameWebSocketHandler.java  # 수신 메시지 → GameCommand 큐 적재
│   ├── GameLoopService.java       # 전용 스케줄러 20틱/초 — 월드 변경·전송은 이 스레드만
│   └── SessionRegistry.java       # 세션 ↔ 플레이어 매핑
└── config/     # GameWorld 빈 구성

src/main/resources/static/   # 클라이언트 (index.html / game.js / style.css)
```

## 아키텍처 원칙
- **서버 권위**: 모든 판정(이동·충돌·성장)은 서버 틱에서 수행. 클라는 입력만 보낸다.
- **단일 스레드 월드**: WebSocket 스레드는 `GameCommand` 큐에 적재만 하고,
  월드 변경과 메시지 전송은 게임 루프 스레드에서만 일어난다 — 락 불필요.
- **먹이 증분 전송**: 입장 시 전체 스냅샷(joined), 이후 added/removed 증분(state).
- **공정 시야**: 클라이언트는 창 크기와 무관하게 고정 시야 반경(VIEW_RADIUS)을
  짧은 변 기준으로 스케일해 그린다 — 큰 창이 유리하지 않다.

## 프로토콜 (JSON, type 필드로 구분)
- 클라→서버: `join{nickname}`, `input{angle, boosting}`
- 서버→클라: `joined{playerId, mapRadius, foods}`, `state{snakes, foodsAdded, foodsRemoved, leaderboard}`, `dead{score}`

## 주의
- 게임 수치 조정은 `GameConfig` 상수만 변경한다.
- `game/` 패키지는 Spring·WebSocket 의존 금지 유지.
