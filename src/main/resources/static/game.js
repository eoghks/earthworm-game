'use strict';

// ===== 상수 =====
const TICK_MS = 50;              // 서버 틱 간격 — 보간 기준
const INPUT_INTERVAL_MS = 50;    // 입력 전송 스로틀 (~초당 20회)
const VIEW_RADIUS = 700;         // 고정 시야 반경(월드 단위) — 창 크기와 무관하게 동일 범위를 보여줘 공정성 유지
const SEGMENT_RADIUS = 10;       // 지렁이 몸 반지름(렌더)
const FOOD_RADIUS = 5;           // 먹이 반지름(렌더)

// ===== DOM =====
const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');
const joinOverlay = document.getElementById('join-overlay');
const deadOverlay = document.getElementById('dead-overlay');
const hud = document.getElementById('hud');
const nicknameInput = document.getElementById('nickname');
const joinBtn = document.getElementById('join-btn');
const respawnBtn = document.getElementById('respawn-btn');
const myScoreEl = document.getElementById('my-score');
const finalScoreEl = document.getElementById('final-score');
const leaderboardList = document.getElementById('leaderboard-list');

// ===== 클라이언트 상태 =====
let ws = null;
let myPlayerId = null;
let myNickname = '';
let mapRadius = 2000;
const foods = new Map();         // id → {x, y}
let prevSnakes = new Map();      // 직전 틱 스냅샷 — 보간용
let currSnakes = new Map();      // 최신 틱 스냅샷
let lastStateTime = 0;
let mouse = { x: 0, y: 0 };
let boosting = false;
let lastSentAngle = null;
let lastSentBoost = false;

// ===== 캔버스 리사이즈 — 창 크기 + devicePixelRatio 반영 =====
function resizeCanvas() {
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.floor(window.innerWidth * dpr);
  canvas.height = Math.floor(window.innerHeight * dpr);
}
window.addEventListener('resize', resizeCanvas);
resizeCanvas();

// 창 크기와 무관하게 같은 월드 범위(VIEW_RADIUS)가 보이도록 짧은 변 기준 스케일 계산
function viewScale() {
  return Math.min(canvas.width, canvas.height) / (2 * VIEW_RADIUS);
}

// ===== WebSocket =====
function connect(nickname) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(`${proto}://${location.host}/ws`);
  ws.onopen = () => ws.send(JSON.stringify({ type: 'join', nickname }));
  ws.onmessage = (event) => handleMessage(JSON.parse(event.data));
  ws.onclose = () => {
    // 연결이 끊기면 입장 화면으로 복귀
    myPlayerId = null;
    hud.classList.add('hidden');
    deadOverlay.classList.add('hidden');
    joinOverlay.classList.remove('hidden');
  };
}

function handleMessage(msg) {
  if (msg.type === 'joined') onJoined(msg);
  else if (msg.type === 'state') onState(msg);
  else if (msg.type === 'dead') onDead(msg);
}

function onJoined(msg) {
  myPlayerId = msg.playerId;
  mapRadius = msg.mapRadius;
  foods.clear();
  msg.foods.forEach((f) => foods.set(f.id, f));
  joinOverlay.classList.add('hidden');
  deadOverlay.classList.add('hidden');
  hud.classList.remove('hidden');
}

function onState(msg) {
  // 먹이는 증분(added/removed)으로 갱신
  msg.foodsAdded.forEach((f) => foods.set(f.id, f));
  msg.foodsRemoved.forEach((id) => foods.delete(id));
  // 지렁이는 직전/최신 두 스냅샷을 유지해 렌더에서 보간
  prevSnakes = currSnakes;
  currSnakes = new Map(msg.snakes.map((s) => [s.id, s]));
  lastStateTime = performance.now();
  updateHud(msg.leaderboard);
}

function onDead(msg) {
  myPlayerId = null;
  finalScoreEl.textContent = msg.score;
  hud.classList.add('hidden');
  deadOverlay.classList.remove('hidden');
}

// ===== HUD =====
function updateHud(leaderboard) {
  const me = myPlayerId ? currSnakes.get(myPlayerId) : null;
  if (me) myScoreEl.textContent = me.segments.length;
  leaderboardList.innerHTML = '';
  leaderboard.forEach((entry) => {
    const li = document.createElement('li');
    li.textContent = `${entry.nickname} — ${entry.score}`;
    if (me && entry.nickname === myNickname) li.classList.add('me');
    leaderboardList.appendChild(li);
  });
}

// ===== 입력 =====
window.addEventListener('mousemove', (e) => { mouse.x = e.clientX; mouse.y = e.clientY; });
window.addEventListener('mousedown', () => { boosting = true; });
window.addEventListener('mouseup', () => { boosting = false; });
window.addEventListener('keydown', (e) => { if (e.code === 'Space') boosting = true; });
window.addEventListener('keyup', (e) => { if (e.code === 'Space') boosting = false; });

// 화면 중앙(내 머리) 기준 마우스 방향 각도를 스로틀 걸어 전송
setInterval(() => {
  if (!ws || ws.readyState !== WebSocket.OPEN || !myPlayerId) return;
  const angle = Math.atan2(mouse.y - window.innerHeight / 2, mouse.x - window.innerWidth / 2);
  if (angle === lastSentAngle && boosting === lastSentBoost) return; // 변화 없으면 생략
  lastSentAngle = angle;
  lastSentBoost = boosting;
  ws.send(JSON.stringify({ type: 'input', angle, boosting }));
}, INPUT_INTERVAL_MS);

// ===== 보간 =====
function lerp(a, b, t) {
  return a + (b - a) * t;
}

// 직전/최신 스냅샷 사이를 lerp — 세그먼트 수가 다르면 최신 값 사용
function interpolatedSegments(snake, t) {
  const prev = prevSnakes.get(snake.id);
  if (!prev) return snake.segments;
  return snake.segments.map((seg, i) => {
    const p = prev.segments[i];
    if (!p) return seg;
    return [lerp(p[0], seg[0], t), lerp(p[1], seg[1], t)];
  });
}

// ===== 렌더 =====
function render() {
  requestAnimationFrame(render);
  const w = canvas.width;
  const h = canvas.height;
  ctx.fillStyle = '#10131a';
  ctx.fillRect(0, 0, w, h);

  const t = Math.min(1, (performance.now() - lastStateTime) / TICK_MS);
  const camera = cameraPosition(t);
  const scale = viewScale();

  ctx.save();
  ctx.translate(w / 2, h / 2);
  ctx.scale(scale, scale);
  ctx.translate(-camera.x, -camera.y);

  drawGrid(camera);
  drawBoundary();
  drawFoods(camera);
  drawSnakes(t);
  ctx.restore();
}

// 카메라는 내 머리(보간 위치)를 따라간다. 미입장/사망 시 마지막 위치 유지
let lastCamera = { x: 0, y: 0 };
function cameraPosition(t) {
  const me = myPlayerId ? currSnakes.get(myPlayerId) : null;
  if (!me) return lastCamera;
  const head = interpolatedSegments(me, t)[0];
  lastCamera = { x: head[0], y: head[1] };
  return lastCamera;
}

// 격자 배경 — 시야 범위만 그린다
function drawGrid(camera) {
  const step = 100;
  const startX = Math.floor((camera.x - VIEW_RADIUS * 2) / step) * step;
  const endX = camera.x + VIEW_RADIUS * 2;
  const startY = Math.floor((camera.y - VIEW_RADIUS * 2) / step) * step;
  const endY = camera.y + VIEW_RADIUS * 2;
  ctx.strokeStyle = 'rgba(255,255,255,0.05)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  for (let x = startX; x <= endX; x += step) {
    ctx.moveTo(x, startY);
    ctx.lineTo(x, endY);
  }
  for (let y = startY; y <= endY; y += step) {
    ctx.moveTo(startX, y);
    ctx.lineTo(endX, y);
  }
  ctx.stroke();
}

// 원형 맵 경계
function drawBoundary() {
  ctx.strokeStyle = '#e0455a';
  ctx.lineWidth = 8;
  ctx.beginPath();
  ctx.arc(0, 0, mapRadius, 0, Math.PI * 2);
  ctx.stroke();
}

// 먹이 — 시야 밖은 컬링
function drawFoods(camera) {
  const cullDist = VIEW_RADIUS * 1.5;
  ctx.fillStyle = '#7bd88f';
  foods.forEach((f) => {
    if (Math.abs(f.x - camera.x) > cullDist || Math.abs(f.y - camera.y) > cullDist) return;
    ctx.beginPath();
    ctx.arc(f.x, f.y, FOOD_RADIUS, 0, Math.PI * 2);
    ctx.fill();
  });
}

// 지렁이 — 꼬리부터 그려 머리가 위에 오게 하고 머리는 색·눈으로 구분
function drawSnakes(t) {
  currSnakes.forEach((snake) => {
    const segs = interpolatedSegments(snake, t);
    const isMe = snake.id === myPlayerId;
    const bodyColor = isMe ? '#3e7bfa' : '#c4913e';
    ctx.fillStyle = snake.boosting ? '#ff8a5c' : bodyColor;
    for (let i = segs.length - 1; i >= 1; i--) {
      ctx.beginPath();
      ctx.arc(segs[i][0], segs[i][1], SEGMENT_RADIUS, 0, Math.PI * 2);
      ctx.fill();
    }
    drawHead(segs, isMe);
    drawNickname(snake, segs);
  });
}

function drawHead(segs, isMe) {
  const head = segs[0];
  ctx.fillStyle = isMe ? '#8fb3ff' : '#e8d5a8';
  ctx.beginPath();
  ctx.arc(head[0], head[1], SEGMENT_RADIUS + 2, 0, Math.PI * 2);
  ctx.fill();
  // 눈 — 진행 방향 기준 양쪽
  const next = segs[1] || head;
  const angle = Math.atan2(head[1] - next[1], head[0] - next[0]);
  ctx.fillStyle = '#10131a';
  [-0.5, 0.5].forEach((offset) => {
    const ex = head[0] + Math.cos(angle + offset) * 6;
    const ey = head[1] + Math.sin(angle + offset) * 6;
    ctx.beginPath();
    ctx.arc(ex, ey, 2.5, 0, Math.PI * 2);
    ctx.fill();
  });
}

function drawNickname(snake, segs) {
  ctx.fillStyle = 'rgba(255,255,255,0.85)';
  ctx.font = '14px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText(snake.nickname, segs[0][0], segs[0][1] - SEGMENT_RADIUS - 8);
}

// ===== 입장/재입장 =====
function join() {
  const nickname = nicknameInput.value.trim() || '이름없는지렁이';
  myNickname = nickname;
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'join', nickname }));
  } else {
    connect(nickname);
  }
}

joinBtn.addEventListener('click', join);
nicknameInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') join(); });
respawnBtn.addEventListener('click', join);

render();
