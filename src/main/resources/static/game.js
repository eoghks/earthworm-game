'use strict';

// ===== 상수 =====
const TICK_MS = 50;              // 서버 틱 간격 — 보간 기준
const INPUT_INTERVAL_MS = 50;    // 입력 전송 스로틀 (~초당 20회)
const VIEW_RADIUS = 700;         // 고정 시야 반경(월드 단위) — 창 크기와 무관하게 동일 범위를 보여줘 공정성 유지
const SEGMENT_RADIUS = 10;       // 지렁이 몸 반지름(렌더)
const FOOD_RADIUS = 5;           // 먹이 반지름(렌더)
const STRIPE_WIDTH = 3;          // 줄무늬 스킨 — 세그먼트 N개 단위로 색 교차

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
// 인증·상점 UI
const authSection = document.getElementById('auth-section');
const userSection = document.getElementById('user-section');
const authUsername = document.getElementById('auth-username');
const authPassword = document.getElementById('auth-password');
const authNickname = document.getElementById('auth-nickname');
const authError = document.getElementById('auth-error');
const loginBtn = document.getElementById('login-btn');
const signupBtn = document.getElementById('signup-btn');
const logoutBtn = document.getElementById('logout-btn');
const userNicknameEl = document.getElementById('user-nickname');
const userCreditEl = document.getElementById('user-credit');
const shopBtn = document.getElementById('shop-btn');
const deadShopBtn = document.getElementById('dead-shop-btn');
const earnedCreditRow = document.getElementById('earned-credit-row');
const earnedCreditEl = document.getElementById('earned-credit');
const shopOverlay = document.getElementById('shop-overlay');
const shopCreditEl = document.getElementById('shop-credit');
const skinListEl = document.getElementById('skin-list');
const shopError = document.getElementById('shop-error');
const shopCloseBtn = document.getElementById('shop-close-btn');

// ===== 클라이언트 상태 =====
let ws = null;
let myPlayerId = null;
let mapRadius = 2000;        // 렌더용 현재 반지름 — 서버 값으로 부드럽게 수렴
let targetMapRadius = 2000;  // 서버가 알려준 최신 맵 반지름
const foods = new Map();         // id → {x, y}
let prevSnakes = new Map();      // 직전 틱 스냅샷 — 보간용
let currSnakes = new Map();      // 최신 틱 스냅샷
let lastStateTime = 0;
let mouse = { x: 0, y: 0 };
let boosting = false;
let lastSentAngle = null;
let lastSentBoost = false;
let me = null;                   // 로그인 회원 정보 (/api/members/me) — 게스트면 null
const skinMap = new Map();       // 스킨 카탈로그: id → {displayName, type, price, primaryColor, secondaryColor}
let signupMode = false;          // 회원가입 폼 노출 여부

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

// ===== REST 헬퍼 — 세션 쿠키 기반이라 별도 토큰 없음 =====
async function api(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error((data && data.message) || `요청 실패 (${res.status})`);
  return data;
}

// 스킨 카탈로그 로드 — 게스트도 타 플레이어 스킨을 그려야 하므로 항상 호출
async function loadCatalog() {
  const skins = await api('GET', '/api/skins').catch(() => []);
  skins.forEach((s) => skinMap.set(s.id, s));
}

// 내 정보 갱신 — 401(게스트)이면 null. fillNickname은 로그인 직후에만 true —
// 라운드마다 사용자가 바꾼 커스텀 닉네임을 덮어쓰지 않기 위함
async function refreshMe(fillNickname = false) {
  me = await api('GET', '/api/members/me').catch(() => null);
  updateAuthUi();
  if (me && fillNickname) nicknameInput.value = me.nickname;
}

// ===== 인증 UI =====
function updateAuthUi() {
  authSection.classList.toggle('hidden', !!me);
  userSection.classList.toggle('hidden', !me);
  if (me) {
    userNicknameEl.textContent = me.nickname;
    userCreditEl.textContent = me.credit;
  }
  deadShopBtn.classList.toggle('hidden', !me);
}

function showAuthError(message) {
  authError.textContent = message;
  authError.classList.toggle('hidden', !message);
}

async function login() {
  const body = { username: authUsername.value.trim(), password: authPassword.value };
  await api('POST', '/api/auth/login', body);
  authPassword.value = '';
  showAuthError('');
  await refreshMe(true); // 로그인 직후에만 입장 닉네임 입력란을 회원 닉네임으로 채운다
}

// 회원가입 — 첫 클릭은 닉네임 입력란 노출, 두 번째 클릭에 제출
async function signup() {
  if (!signupMode) {
    signupMode = true;
    authNickname.classList.remove('hidden');
    authNickname.focus();
    return;
  }
  const body = {
    username: authUsername.value.trim(),
    password: authPassword.value,
    nickname: authNickname.value.trim(),
  };
  await api('POST', '/api/auth/signup', body);
  await login(); // 가입 즉시 로그인
  signupMode = false;
  authNickname.classList.add('hidden');
}

async function logout() {
  await api('POST', '/api/auth/logout').catch(() => {});
  me = null;
  updateAuthUi();
}

loginBtn.addEventListener('click', () => login().catch((e) => showAuthError(e.message)));
signupBtn.addEventListener('click', () => signup().catch((e) => showAuthError(e.message)));
logoutBtn.addEventListener('click', () => logout());

// ===== 스킨 상점 =====
function openShop() {
  shopError.classList.add('hidden');
  renderShop();
  shopOverlay.classList.remove('hidden');
  // 적립은 서버에서 비동기 커밋이라 직전 조회가 구버전일 수 있다 — 최신 잔액으로 한 번 더 그린다
  if (me) refreshMe().then(renderShop);
}

function renderShop() {
  shopCreditEl.textContent = me ? me.credit : 0;
  skinListEl.innerHTML = '';
  skinMap.forEach((skin) => skinListEl.appendChild(buildSkinItem(skin)));
}

// 스킨 한 줄: 미리보기 · 이름 · 가격 · 구매/장착 버튼
function buildSkinItem(skin) {
  const owned = !!me && me.ownedSkinIds.includes(skin.id);
  const equipped = !!me && me.equippedSkinId === skin.id;
  const item = document.createElement('div');
  item.className = 'skin-item' + (equipped ? ' equipped' : '');

  const preview = document.createElement('div');
  preview.className = 'skin-preview';
  preview.style.background = previewBackground(skin);
  item.appendChild(preview);

  const name = document.createElement('span');
  name.className = 'skin-name';
  name.textContent = skin.displayName;
  item.appendChild(name);

  item.appendChild(buildSkinAction(skin, owned, equipped));
  return item;
}

// 상태별 액션: 장착 중 표시 / 장착 버튼 / 구매 버튼
function buildSkinAction(skin, owned, equipped) {
  if (equipped) {
    const state = document.createElement('span');
    state.className = 'skin-state';
    state.textContent = '장착 중';
    return state;
  }
  const btn = document.createElement('button');
  if (owned) {
    btn.textContent = '장착';
    btn.addEventListener('click', () => shopAction(`/api/skins/${skin.id}/equip`));
  } else {
    btn.textContent = skin.price === 0 ? '무료' : `구매 (${skin.price})`;
    btn.addEventListener('click', () => shopAction(`/api/skins/${skin.id}/purchase`));
  }
  return btn;
}

// 구매·장착 공통 처리 — 성공 시 내 정보 갱신 후 목록 다시 그림
function shopAction(url) {
  api('POST', url)
    .then(() => refreshMe())
    .then(() => {
      shopError.classList.add('hidden');
      renderShop();
    })
    .catch((e) => {
      shopError.textContent = e.message;
      shopError.classList.remove('hidden');
    });
}

// 미리보기 배경 — 단색/줄무늬/그라데이션을 CSS로 표현
function previewBackground(skin) {
  if (skin.type === 'STRIPE') {
    return `repeating-linear-gradient(90deg, ${skin.primaryColor} 0 8px, ${skin.secondaryColor} 8px 16px)`;
  }
  if (skin.type === 'GRADIENT') {
    return `linear-gradient(90deg, ${skin.primaryColor}, ${skin.secondaryColor})`;
  }
  return skin.primaryColor;
}

shopBtn.addEventListener('click', openShop);
deadShopBtn.addEventListener('click', openShop);
shopCloseBtn.addEventListener('click', () => shopOverlay.classList.add('hidden'));

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
  // 입장 시점 맵 크기는 초기값 — 이후 state의 mapRadius가 우선한다
  mapRadius = msg.mapRadius;
  targetMapRadius = msg.mapRadius;
  foods.clear();
  msg.foods.forEach((f) => foods.set(f.id, f));
  joinOverlay.classList.add('hidden');
  deadOverlay.classList.add('hidden');
  shopOverlay.classList.add('hidden');
  hud.classList.remove('hidden');
}

function onState(msg) {
  // 동적 맵 크기 — 렌더 루프에서 현재 값을 이 목표로 보간한다
  targetMapRadius = msg.mapRadius;
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
  // 로그인 상태면 이번 라운드 획득 크레딧 표시 + 잔액 갱신
  earnedCreditRow.classList.toggle('hidden', !me);
  earnedCreditEl.textContent = msg.creditEarned;
  if (me) {
    refreshMe();
    // 적립이 비동기 커밋이라 첫 조회가 커밋보다 앞설 수 있다 — 짧은 지연 후 한 번 더 갱신
    setTimeout(() => refreshMe(), 500);
  }
  hud.classList.add('hidden');
  deadOverlay.classList.remove('hidden');
}

// ===== HUD =====
function updateHud(leaderboard) {
  const mySnake = myPlayerId ? currSnakes.get(myPlayerId) : null;
  if (mySnake) myScoreEl.textContent = mySnake.segments.length;
  leaderboardList.innerHTML = '';
  leaderboard.forEach((entry) => {
    const li = document.createElement('li');
    li.textContent = `${entry.nickname} — ${entry.score}`;
    // playerId 기준 판별 — 동명이인 오강조 방지
    if (entry.playerId === myPlayerId) li.classList.add('me');
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
  // 경계 원을 서버가 알려준 반지름으로 부드럽게 수렴시킨다
  mapRadius = lerp(mapRadius, targetMapRadius, 0.1);
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
  const mySnake = myPlayerId ? currSnakes.get(myPlayerId) : null;
  if (!mySnake) return lastCamera;
  const head = interpolatedSegments(mySnake, t)[0];
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

// ===== 스킨 색상 =====
// 세그먼트별 채움색 — 기본 스킨은 기존 색(내 지렁이 파랑·타인 금색·부스트 주황) 유지
function bodyFill(snake, skin, index, total) {
  if (!skin || skin.id === 'default') {
    const base = snake.id === myPlayerId ? '#3e7bfa' : '#c4913e';
    return snake.boosting ? '#ff8a5c' : base;
  }
  if (skin.type === 'STRIPE') {
    return Math.floor(index / STRIPE_WIDTH) % 2 === 0 ? skin.primaryColor : skin.secondaryColor;
  }
  if (skin.type === 'GRADIENT') {
    return mixHex(skin.primaryColor, skin.secondaryColor, total <= 1 ? 0 : index / (total - 1));
  }
  return skin.primaryColor;
}

// 두 hex 색상 보간 → rgb 문자열
function mixHex(a, b, t) {
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(lerp((pa >> 16) & 255, (pb >> 16) & 255, t));
  const g = Math.round(lerp((pa >> 8) & 255, (pb >> 8) & 255, t));
  const bl = Math.round(lerp(pa & 255, pb & 255, t));
  return `rgb(${r},${g},${bl})`;
}

// 지렁이 — 꼬리부터 그려 머리가 위에 오게 하고 머리는 색·눈으로 구분
function drawSnakes(t) {
  currSnakes.forEach((snake) => {
    const segs = interpolatedSegments(snake, t);
    const skin = skinMap.get(snake.skinId);
    const isMe = snake.id === myPlayerId;
    for (let i = segs.length - 1; i >= 1; i--) {
      ctx.fillStyle = bodyFill(snake, skin, i, segs.length);
      ctx.beginPath();
      ctx.arc(segs[i][0], segs[i][1], SEGMENT_RADIUS, 0, Math.PI * 2);
      ctx.fill();
    }
    // 스킨 착용 중 부스트는 몸색 대신 머리 주변 광륜으로 표시
    if (snake.boosting && skin && skin.id !== 'default') drawBoostHalo(segs[0]);
    drawHead(segs, isMe);
    drawNickname(snake, segs);
  });
}

// 부스트 광륜 — 스킨 색을 가리지 않으면서 부스트 상태를 드러낸다
function drawBoostHalo(head) {
  ctx.strokeStyle = 'rgba(255,138,92,0.8)';
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.arc(head[0], head[1], SEGMENT_RADIUS + 6, 0, Math.PI * 2);
  ctx.stroke();
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
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'join', nickname }));
  } else {
    connect(nickname);
  }
}

joinBtn.addEventListener('click', join);
nicknameInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') join(); });
respawnBtn.addEventListener('click', join);

// ===== 초기화 =====
loadCatalog();
refreshMe(true); // 세션이 살아있는 재방문도 로그인과 동일하게 닉네임을 채운다
render();
