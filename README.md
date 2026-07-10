# 지렁이 게임 (snake-game)

slither.io 스타일 실시간 멀티플레이 지렁이 게임 MVP — 서버 권위(authoritative) 구조.

## 실행

```bash
.\gradlew.bat bootRun
```

브라우저에서 http://localhost:8085 접속 → 닉네임 입력 → 입장.

## 조작
- **마우스**: 지렁이가 마우스 방향으로 이동
- **마우스 클릭 / 스페이스**: 부스트 (길이를 소모하며 가속, 소모분은 먹이로 배출)

## 규칙
- 먹이를 먹으면 성장, 길이 기준 상위 10명이 리더보드에 노출
- 내 머리가 다른 지렁이 몸이나 맵 경계에 닿으면 사망 — 몸 전체가 먹이로 변환
- 사망 후 다시 입장 가능

## 기술
- Java 21 / Spring Boot 3.5 / 순수 WebSocket(JSON) / 서버 20틱
- 클라이언트: HTML5 Canvas + Vanilla JS (보간 렌더링, 반응형 캔버스)

## 빌드·테스트

```bash
.\gradlew.bat build   # 테스트 포함
.\gradlew.bat test    # 게임 로직 단위 테스트
```
