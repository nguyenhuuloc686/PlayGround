(() => {
  const canvas = document.getElementById('game');
  const ctx = canvas.getContext('2d');
  const scoreEl = document.getElementById('score');
  const livesEl = document.getElementById('lives');
  const overlay = document.getElementById('overlay');
  const overlayText = document.getElementById('overlay-text');
  const playBtn = document.getElementById('play');

  const COLORS = ['#ff5d5d', '#ffd34d', '#5dd8ff', '#8bff7a', '#c58bff'];
  const FRUITS = ['🍎','⭐','🥭','🍑'];
  const MAX_LIVES = 3;

  let W = 0, H = 0, dpr = 1;
  let state = 'menu';
  let score = 0, lives = MAX_LIVES, speedMul = 1, elapsed = 0;
  let spawnTimer = 0, dropTimer = 0;
  let playerX = 0, playerY = 0, playerR = 0, goalY = 0, hOffset = 0;
  let drops = [], popups = [], clouds = [];
  let last = performance.now();

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = window.innerWidth;
    H = window.innerHeight;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    playerR = Math.max(28, Math.min(W, H) * 0.07);
    playerY = H - Math.max(70, H * 0.12);
    if (state === 'menu') playerX = W / 2;
    clouds = Array.from({ length: 5 }, () => ({
      x: Math.random() * W,
      y: Math.random() * H * 0.4,
      s: 30 + Math.random() * 60,
      v: 6 + Math.random() * 14
    }));
    drawMenu();
  }

  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', () => setTimeout(resize, 250));

  function moveFromEvent(e) {
    const x = (e.touches ? e.touches[0].clientX : e.clientX);
    const y = (e.touches ? e.touches[0].clientY : e.clientY);
    if (x !== undefined) playerX = Math.max(playerR, Math.min(W - playerR, x));
    if (y !== undefined && y < H * 0.85) playerY = Math.max(H * 0.3, Math.min(H - 40, y));
  }

  canvas.addEventListener('touchstart', e => { e.preventDefault(); moveFromEvent(e); }, { passive: false });
  canvas.addEventListener('touchmove', e => { e.preventDefault(); moveFromEvent(e); }, { passive: false });
  canvas.addEventListener('touchend', e => e.preventDefault(), { passive: false });
  canvas.addEventListener('pointerdown', e => { canvas.setPointerCapture(e.pointerId); moveFromEvent(e); });
  canvas.addEventListener('pointermove', e => { if (e.buttons || e.pointerType === 'mouse') moveFromEvent(e); });

  playBtn.addEventListener('click', startGame);

  function startGame() {
    score = 0; lives = MAX_LIVES; speedMul = 1; elapsed = 0;
    spawnTimer = 0; dropTimer = 0;
    drops = []; popups = [];
    playerX = W / 2; playerY = H - Math.max(70, H * 0.12);
    updateHud();
    overlay.classList.add('hidden');
    state = 'playing';
    last = performance.now();
    requestAnimationFrame(loop);
  }

  function gameOver() {
    state = 'over';
    overlayText.textContent = `Bạn đạt ${score} điểm — chơi lại nhé!`;
    playBtn.textContent = 'Chơi lại';
    overlay.classList.remove('hidden');
  }

  function updateHud() {
    scoreEl.textContent = score;
    livesEl.textContent = '❤'.repeat(Math.max(0, lives)) + '·'.repeat(MAX_LIVES - Math.max(0, lives));
  }

  function spawnDrop() {
    const r = Math.max(18, Math.min(W, H) * 0.032);
    drops.push({
      x: playerR + Math.random() * (W - playerR * 2),
      y: -r * 2,
      r,
      v: (H * 0.18 + Math.random() * H * 0.14) * speedMul,
      rot: Math.random() * Math.PI * 2,
      vr: (Math.random() - 0.5) * 3,
      color: COLORS[(Math.random() * COLORS.length) | 0],
      fruit: FRUITS[(Math.random() * FRUITS.length) | 0]
    });
  }

  function loop(now) {
    if (state !== 'playing') return;
    let dt = Math.min((now - last) / 1000, 0.05);
    last = now;
    elapsed += dt;
    speedMul = 1 + Math.min(elapsed / 30, 2.2);

    spawnTimer -= dt;
    if (spawnTimer <= 0) { spawnDrop(); spawnTimer = Math.max(0.28, 0.95 - speedMul * 0.12); }

    for (const d of drops) {
      d.y += d.v * dt;
      d.rot += d.vr * dt;
    }

    for (let i = drops.length - 1; i >= 0; i--) {
      const d = drops[i];
      const dx = d.x - playerX, dy = d.y - playerY;
      if (dx * dx + dy * dy < (d.r + playerR) * (d.r + playerR)) {
        drops.splice(i, 1);
        score += 10 + (elapsed > 20 ? Math.round(speedMul * 4) : 0);
        popups.push({ x: d.x, y: d.y, t: 0, text: `+${10 + (elapsed > 20 ? Math.round(speedMul * 4) : 0)}`, color: d.color });
        updateHud();
      } else if (d.y > H + d.r * 2) {
        drops.splice(i, 1);
        lives--;
        updateHud();
        if (lives <= 0) { drawScene(dt); gameOver(); return; }
      }
    }

    for (const p of popups) p.t += dt;
    popups = popups.filter(p => p.t < 0.8);

    drawScene(dt);
    requestAnimationFrame(loop);
  }

  function drawBackground(dt) {
    const sky = ctx.createLinearGradient(0, 0, 0, H);
    sky.addColorStop(0, '#1b2350');
    sky.addColorStop(0.55, '#34407e');
    sky.addColorStop(1, '#5b4a9e');
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, W, H);

    const t = performance.now() / 1000;
    ctx.fillStyle = 'rgba(255,255,255,0.10)';
    for (let i = 0; i < 24; i++) {
      const sx = ((i * 137 + 40) % W + t * 8 * ((i % 3) + 1)) % W;
      const sy = (i * 97 + 20) % (H * 0.6);
      ctx.beginPath();
      ctx.arc(sx, sy, 1.5 + (i % 3), 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.fillStyle = 'rgba(255,255,255,0.13)';
    for (const c of clouds) {
      c.x -= c.v * (dt || 0.016);
      if (c.x < -c.s * 2) c.x = W + c.s * 2;
      ctx.beginPath();
      ctx.ellipse(c.x, c.y, c.s, c.s * 0.42, 0, 0, Math.PI * 2);
      ctx.fill();
    }

    const groundH = H * 0.085;
    ctx.fillStyle = '#2a2f55';
    ctx.fillRect(0, H - groundH, W, groundH);
    ctx.fillStyle = 'rgba(255,255,255,0.06)';
    ctx.fillRect(0, H - groundH, W, 4);
  }

  function drawPlayer() {
    ctx.save();
    ctx.translate(playerX, playerY);
    ctx.shadowColor = 'rgba(0,0,0,0.4)';
    ctx.shadowBlur = 14;
    ctx.shadowOffsetY = 6;
    ctx.fillStyle = '#ff7a4d';
    ctx.beginPath();
    ctx.arc(0, 0, playerR, Math.PI, 0);
    ctx.lineTo(playerR * 0.9, playerR * 0.55);
    ctx.lineTo(playerR, playerR * 1.15);
    ctx.lineTo(0, playerR * 0.85);
    ctx.lineTo(-playerR, playerR * 1.15);
    ctx.lineTo(-playerR * 0.9, playerR * 0.55);
    ctx.closePath();
    ctx.fill();
    ctx.shadowColor = 'transparent';

    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(-playerR * 0.35, -playerR * 0.28, playerR * 0.16, 0, Math.PI * 2);
    ctx.arc(playerR * 0.35, -playerR * 0.28, playerR * 0.16, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#28304f';
    ctx.beginPath();
    ctx.arc(-playerR * 0.35, -playerR * 0.24, playerR * 0.08, 0, Math.PI * 2);
    ctx.arc(playerR * 0.35, -playerR * 0.24, playerR * 0.08, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = '#28304f';
    ctx.lineWidth = Math.max(2, playerR * 0.07);
    ctx.beginPath();
    ctx.arc(0, playerR * 0.05, playerR * 0.38, 0.15 * Math.PI, 0.85 * Math.PI);
    ctx.stroke();

    ctx.strokeStyle = 'rgba(255,255,255,0.55)';
    ctx.lineWidth = 3;
    ctx.setLineDash([8, 8]);
    ctx.beginPath();
    ctx.moveTo(-playerR * 1.5, playerR * 1.6);
    ctx.lineTo(playerR * 1.5, playerR * 1.6);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
  }

  function drawScene(dt) {
    drawBackground(dt);
    drawPlayer();

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (const d of drops) {
      ctx.save();
      ctx.translate(d.x, d.y);
      ctx.rotate(d.rot);
      ctx.font = `${d.r * 1.7}px "Segoe UI Emoji", "Apple Color Emoji", sans-serif`;
      ctx.fillText(d.fruit, 0, 0);
      ctx.restore();
    }

    for (const p of popups) {
      ctx.globalAlpha = 1 - p.t / 0.8;
      ctx.fillStyle = p.color;
      ctx.font = `bold ${Math.max(16, p.y * 0.03 + 16)}px "Trebuchet MS", sans-serif`;
      ctx.fillText(p.text, p.x, p.y - p.t * 60);
      ctx.globalAlpha = 1;
    }
  }

  function drawMenu() {
    if (state !== 'menu') return;
    drawBackground(0.016);
    playerX = W / 2;
    drawPlayer();
  }

  resize();
  state = 'menu';
  window.__game = { ready: true, start: startGame };
})();
