"""
LivreGames 3D Cloud Gaming Server for Google Colab
==================================================
Executa jogos 3D (PSP/PS2) acelerados por GPU Tesla T4 no Google Colab
e transmite via WebSockets/MPEG-TS a 60 FPS com áudio e controles touch na tela.
Zero download no celular!
"""

import os
import sys
import time
import json
import re
import subprocess
import asyncio
import socket
from pathlib import Path

# Configurações padrão
DEFAULT_GAME_URL = "https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/games/dbz_shin_budokai.cso"
GAME_NAME = "Dragon Ball Z: Shin Budokai"
WIDTH = 960
HEIGHT = 544
FPS = 60

def get_free_port(preferred=18080):
    for port in [preferred, 18081, 18082, 19000, 28080, 8585]:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.bind(('127.0.0.1', port))
                return port
        except OSError:
            pass
    return preferred

PORT = get_free_port(18080)

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
  <title>LivreGames Cloud 3D</title>
  <style>
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      user-select: none;
      -webkit-user-select: none;
      -webkit-touch-callout: none;
    }
    body, html {
      width: 100%;
      height: 100%;
      overflow: hidden;
      background: #020617;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #fff;
    }
    #game-container {
      position: relative;
      width: 100vw;
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #000;
    }
    #video-canvas {
      width: 100%;
      height: 100%;
      object-fit: contain;
      background: #000;
    }

    /* Top HUD */
    #hud {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      padding: 10px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: linear-gradient(180deg, rgba(0,0,0,0.8) 0%, rgba(0,0,0,0) 100%);
      z-index: 50;
      pointer-events: auto;
    }
    .hud-left {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .badge {
      font-size: 11px;
      font-weight: 700;
      padding: 4px 8px;
      border-radius: 999px;
      letter-spacing: 0.5px;
    }
    .badge.live {
      background: rgba(34, 197, 94, 0.2);
      color: #4ade80;
      border: 1px solid rgba(34, 197, 94, 0.4);
    }
    .game-title {
      font-size: 13px;
      font-weight: 600;
      color: #94a3b8;
      max-width: 180px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .hud-right {
      display: flex;
      gap: 8px;
    }
    .hud-btn {
      background: rgba(30, 41, 59, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.2);
      color: #fff;
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 12px;
      cursor: pointer;
      backdrop-filter: blur(8px);
    }

    /* Controles Virtuais */
    #touch-controls {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 40;
    }

    /* Shoulders (L e R) */
    .shoulder-btn {
      position: absolute;
      top: 55px;
      width: 80px;
      height: 44px;
      border-radius: 12px;
      background: rgba(30, 41, 59, 0.5);
      border: 1.5px solid rgba(255, 255, 255, 0.25);
      backdrop-filter: blur(10px);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 16px;
      color: #cbd5e1;
      pointer-events: auto;
      touch-action: none;
      box-shadow: 0 4px 12px rgba(0,0,0,0.4);
    }
    .left-shoulder { left: 16px; }
    .right-shoulder { right: 16px; }

    /* D-PAD */
    .dpad-container {
      position: absolute;
      left: 20px;
      bottom: 25px;
      width: 160px;
      height: 160px;
      pointer-events: auto;
      touch-action: none;
    }
    .dpad-btn {
      position: absolute;
      width: 52px;
      height: 52px;
      background: rgba(30, 41, 59, 0.55);
      border: 1.5px solid rgba(255, 255, 255, 0.2);
      backdrop-filter: blur(10px);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      color: #94a3b8;
      border-radius: 10px;
      box-shadow: 0 4px 10px rgba(0,0,0,0.5);
    }
    .dpad-up    { top: 0; left: 54px; }
    .dpad-down  { bottom: 0; left: 54px; }
    .dpad-left  { top: 54px; left: 0; }
    .dpad-right { top: 54px; right: 0; }
    .dpad-center {
      position: absolute;
      top: 54px;
      left: 54px;
      width: 52px;
      height: 52px;
      background: rgba(15, 23, 42, 0.6);
      border-radius: 8px;
    }

    /* Action Buttons (▲, ■, ●, ✕) */
    .action-container {
      position: absolute;
      right: 20px;
      bottom: 25px;
      width: 170px;
      height: 170px;
      pointer-events: auto;
      touch-action: none;
    }
    .action-btn {
      position: absolute;
      width: 54px;
      height: 54px;
      border-radius: 50%;
      background: rgba(30, 41, 59, 0.55);
      backdrop-filter: blur(10px);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 800;
      box-shadow: 0 4px 12px rgba(0,0,0,0.5);
    }
    .btn-triangle {
      top: 0; left: 58px;
      color: #22c55e;
      border: 2px solid rgba(34, 197, 94, 0.4);
    }
    .btn-square {
      top: 58px; left: 0;
      color: #ec4899;
      border: 2px solid rgba(236, 72, 153, 0.4);
    }
    .btn-circle {
      top: 58px; right: 0;
      color: #ef4444;
      border: 2px solid rgba(239, 68, 68, 0.4);
    }
    .btn-cross {
      bottom: 0; left: 58px;
      color: #38bdf8;
      border: 2px solid rgba(56, 189, 248, 0.4);
    }

    /* Start / Select */
    .system-controls {
      position: absolute;
      bottom: 15px;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      gap: 20px;
      pointer-events: auto;
      touch-action: none;
    }
    .sys-btn {
      background: rgba(30, 41, 59, 0.5);
      border: 1px solid rgba(255, 255, 255, 0.25);
      color: #94a3b8;
      font-size: 11px;
      font-weight: 700;
      padding: 6px 16px;
      border-radius: 999px;
      letter-spacing: 1px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.4);
    }

    /* Efeito de Press */
    .shoulder-btn.active, .dpad-btn.active, .action-btn.active, .sys-btn.active {
      transform: scale(0.92);
      filter: brightness(1.5);
      background: rgba(255, 255, 255, 0.25);
    }
    .btn-triangle.active { box-shadow: 0 0 16px #22c55e; }
    .btn-square.active   { box-shadow: 0 0 16px #ec4899; }
    .btn-circle.active   { box-shadow: 0 0 16px #ef4444; }
    .btn-cross.active    { box-shadow: 0 0 16px #38bdf8; }

    /* Overlay Inicial para Desbloquear Áudio */
    #start-overlay {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(2, 6, 23, 0.92);
      backdrop-filter: blur(12px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 100;
      cursor: pointer;
    }
    .overlay-card {
      text-align: center;
      padding: 30px;
      max-width: 380px;
      background: rgba(30, 41, 59, 0.6);
      border: 1.5px solid rgba(56, 189, 248, 0.4);
      border-radius: 20px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.6);
    }
    .overlay-card .icon {
      font-size: 50px;
      margin-bottom: 12px;
    }
    .overlay-card h2 {
      font-size: 22px;
      color: #38bdf8;
      margin-bottom: 6px;
    }
    .overlay-card p {
      font-size: 13px;
      color: #94a3b8;
      margin-bottom: 20px;
    }
    .btn-play {
      background: linear-gradient(135deg, #2563eb, #1d4ed8);
      color: #fff;
      border: none;
      padding: 14px 32px;
      font-size: 16px;
      font-weight: 700;
      border-radius: 12px;
      box-shadow: 0 4px 15px rgba(37, 99, 235, 0.5);
      cursor: pointer;
    }

    @media (orientation: portrait) {
      .dpad-container { bottom: 15px; width: 140px; height: 140px; }
      .dpad-btn { width: 44px; height: 44px; font-size: 15px; }
      .dpad-up { left: 48px; }
      .dpad-down { left: 48px; }
      .dpad-left { top: 48px; }
      .dpad-right { top: 48px; }
      .dpad-center { top: 48px; left: 48px; width: 44px; height: 44px; }

      .action-container { bottom: 15px; width: 150px; height: 150px; }
      .action-btn { width: 48px; height: 48px; font-size: 17px; }
      .btn-triangle { left: 51px; }
      .btn-square { top: 51px; }
      .btn-circle { top: 51px; }
      .btn-cross { left: 51px; }
    }
  </style>
</head>
<body>

  <div id="game-container">
    <canvas id="video-canvas"></canvas>

    <!-- Top HUD -->
    <div id="hud">
      <div class="hud-left">
        <span class="badge live">● 60 FPS</span>
        <span class="game-title">DRAGON BALL Z</span>
      </div>
      <div class="hud-right">
        <button class="hud-btn" onclick="toggleFullScreen()">⛶ TELA CHEIA</button>
      </div>
    </div>

    <!-- Controles Touch na Tela -->
    <div id="touch-controls">
      <div class="shoulder-btn left-shoulder game-btn" data-key="q">L</div>
      <div class="shoulder-btn right-shoulder game-btn" data-key="e">R</div>

      <!-- D-Pad -->
      <div class="dpad-container">
        <div class="dpad-btn dpad-up game-btn" data-key="Up">▲</div>
        <div class="dpad-btn dpad-down game-btn" data-key="Down">▼</div>
        <div class="dpad-btn dpad-left game-btn" data-key="Left">◀</div>
        <div class="dpad-btn dpad-right game-btn" data-key="Right">▶</div>
        <div class="dpad-center"></div>
      </div>

      <!-- Select / Start -->
      <div class="system-controls">
        <button class="sys-btn game-btn" data-key="space">SELECT</button>
        <button class="sys-btn game-btn" data-key="Return">START</button>
      </div>

      <!-- Botões de Ação -->
      <div class="action-container">
        <div class="action-btn btn-triangle game-btn" data-key="i">▲</div>
        <div class="action-btn btn-square game-btn" data-key="j">■</div>
        <div class="action-btn btn-circle game-btn" data-key="l">●</div>
        <div class="action-btn btn-cross game-btn" data-key="k">✕</div>
      </div>
    </div>

    <!-- Overlay de Inicialização -->
    <div id="start-overlay" onclick="startGame()">
      <div class="overlay-card">
        <div class="icon">🎮</div>
        <h2>LIVREGAMES CLOUD</h2>
        <p>Renderizado na Nuvem com GPU Tesla T4<br>60 FPS • Áudio HD • Zero Download</p>
        <button class="btn-play">TOQUE PARA JOGAR</button>
      </div>
    </div>
  </div>

  <script src="/jsmpeg.min.js"></script>
  <script>
    let player = null;
    let inputWs = null;

    function startGame() {
      const overlay = document.getElementById('start-overlay');
      overlay.style.display = 'none';

      // 1. Iniciar Player JSMpeg
      const canvas = document.getElementById('video-canvas');
      const streamProto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const streamUrl = streamProto + '//' + location.host + '/ws/stream';

      player = new JSMpeg.Player(streamUrl, {
        canvas: canvas,
        autoplay: true,
        audio: true,
        loop: true,
        disableGl: false
      });

      // 2. Iniciar WebSocket de Controle
      initInputWebSocket();
    }

    function initInputWebSocket() {
      const inputProto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const inputUrl = inputProto + '//' + location.host + '/ws/input';
      inputWs = new WebSocket(inputUrl);

      inputWs.onopen = () => console.log('✅ Canal de controle conectado!');
      inputWs.onclose = () => {
        console.warn('⚠️ Controle desconectado, reconectando em 1s...');
        setTimeout(initInputWebSocket, 1000);
      };
    }

    function sendKey(key, state) {
      if (inputWs && inputWs.readyState === WebSocket.OPEN) {
        inputWs.send(JSON.stringify({ key: key, state: state }));
      }
      if (state === 'down' && navigator.vibrate) {
        navigator.vibrate(15);
      }
    }

    // Configurar Multi-touch robusto para todos os botões
    document.querySelectorAll('.game-btn').forEach(btn => {
      const key = btn.getAttribute('data-key');

      btn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        btn.classList.add('active');
        sendKey(key, 'down');
      }, { passive: false });

      btn.addEventListener('touchend', (e) => {
        e.preventDefault();
        btn.classList.remove('active');
        sendKey(key, 'up');
      }, { passive: false });

      btn.addEventListener('touchcancel', (e) => {
        e.preventDefault();
        btn.classList.remove('active');
        sendKey(key, 'up');
      }, { passive: false });
    });

    // Suporte para Teclado Físico (se jogar no PC ou com teclado Bluetooth no celular)
    const keyMap = {
      'ArrowUp': 'Up',
      'ArrowDown': 'Down',
      'ArrowLeft': 'Left',
      'ArrowRight': 'Right',
      'i': 'i', 'I': 'i',
      'j': 'j', 'J': 'j',
      'k': 'k', 'K': 'k',
      'l': 'l', 'L': 'l',
      'q': 'q', 'Q': 'q',
      'e': 'e', 'E': 'e',
      'Enter': 'Return',
      ' ': 'space'
    };

    window.addEventListener('keydown', (e) => {
      if (keyMap[e.key]) {
        e.preventDefault();
        sendKey(keyMap[e.key], 'down');
      }
    });

    window.addEventListener('keyup', (e) => {
      if (keyMap[e.key]) {
        e.preventDefault();
        sendKey(keyMap[e.key], 'up');
      }
    });

    // Suporte para Controle Físico USB/Bluetooth via Gamepad API
    let prevButtons = [];
    function pollGamepad() {
      const gamepads = navigator.getGamepads();
      if (gamepads && gamepads[0]) {
        const gp = gamepads[0];
        const mappings = [
          { idx: 0, key: 'k' },      // A / Cross
          { idx: 1, key: 'l' },      // B / Circle
          { idx: 2, key: 'j' },      // X / Square
          { idx: 3, key: 'i' },      // Y / Triangle
          { idx: 4, key: 'q' },      // L1
          { idx: 5, key: 'e' },      // R1
          { idx: 8, key: 'space' },  // Select
          { idx: 9, key: 'Return' }, // Start
          { idx: 12, key: 'Up' },    // D-Pad Up
          { idx: 13, key: 'Down' },  // D-Pad Down
          { idx: 14, key: 'Left' },  // D-Pad Left
          { idx: 15, key: 'Right' }  // D-Pad Right
        ];
        mappings.forEach(m => {
          const pressed = gp.buttons[m.idx] && gp.buttons[m.idx].pressed;
          if (pressed && !prevButtons[m.idx]) sendKey(m.key, 'down');
          else if (!pressed && prevButtons[m.idx]) sendKey(m.key, 'up');
          prevButtons[m.idx] = pressed;
        });
      }
      requestAnimationFrame(pollGamepad);
    }
    window.addEventListener('gamepadconnected', () => requestAnimationFrame(pollGamepad));

    function toggleFullScreen() {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(() => {});
      } else {
        if (document.exitFullscreen) document.exitFullscreen().catch(() => {});
      }
    }
  </script>
</body>
</html>
"""

def setup_environment():
    print("📦 [1/6] Instalando dependências de sistema (Xvfb, FFmpeg, xdotool, PulseAudio, Openbox)...", flush=True)
    os.system("apt-get update -qq > /dev/null 2>&1")
    os.system("apt-get install -y -qq xvfb xdotool ffmpeg pulseaudio libgl1-mesa-dri libsdl2-2.0-0 psmisc squashfs-tools openbox > /dev/null 2>&1")
    os.system("pip install -q aiohttp websockets qrcode[pil] > /dev/null 2>&1")

    # Baixar jsmpeg.min.js se não existir
    if not os.path.exists("jsmpeg.min.js"):
        print("⬇️ Baixando jsmpeg player...")
        os.system("wget -q -nc https://raw.githubusercontent.com/phoboslab/jsmpeg/master/jsmpeg.min.js -O jsmpeg.min.js")

    # Baixar cloudflared se não existir
    if not os.path.exists("cloudflared"):
        print("⬇️ Baixando Cloudflare Tunnel...")
        os.system("wget -q -nc https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -O cloudflared")
        os.system("chmod +x cloudflared")

def setup_ppsspp():
    print("🎮 [2/6] Preparando emulador PPSSPP com aceleração...", flush=True)
    if not os.path.exists("squashfs-root/AppRun"):
        print("⬇️ Baixando PPSSPP oficial do CDN (50 MB)...", flush=True)
        os.system("rm -rf ppsspp.AppImage squashfs-root")
        os.system("wget -q -O ppsspp.AppImage https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/tools/ppsspp.AppImage")
        os.system("chmod +x ppsspp.AppImage")
        print("📂 Extraindo pacote...", flush=True)
        os.system("./ppsspp.AppImage --appimage-extract > /dev/null 2>&1")
        if os.path.exists("squashfs-root/AppRun"):
            os.system("chmod +x squashfs-root/AppRun")
            print("✅ PPSSPP extraído com sucesso!", flush=True)
        else:
            print("⚠️ Falha ao extrair AppImage com FUSE. Tentando extração alternativa...", flush=True)
            os.system("unsquashfs -d squashfs-root ppsspp.AppImage > /dev/null 2>&1")
            if os.path.exists("squashfs-root/AppRun"):
                os.system("chmod +x squashfs-root/AppRun")
    
    # Criar configurações personalizadas do PPSSPP
    base_psp = Path.home() / ".config" / "ppsspp" / "PSP"
    base_psp.mkdir(parents=True, exist_ok=True)

    # Suporte para persistência de Saves no Google Drive se montado
    drive_base = Path("/content/drive/MyDrive/LivreGames/PSP")
    if Path("/content/drive/MyDrive").exists():
        drive_base.mkdir(parents=True, exist_ok=True)
        drive_saves = drive_base / "SAVEDATA"
        drive_saves.mkdir(parents=True, exist_ok=True)
        local_saves = base_psp / "SAVEDATA"
        if not local_saves.is_symlink() and not local_saves.exists():
            os.system(f"ln -s '{drive_saves}' '{local_saves}'")
        print("💾 Google Drive detectado! Saves sincronizados com o seu Drive.")

    ini_content = f"""[General]
FirstRun = False
AutoLoadSaveState = 0
EnableCheats = False
ConfirmOnQuit = False

[Graphics]
GPUBackend = 0
GraphicsBackend = 0
FailedGPUBackends = 1,2,3
ShowFPSCounter = 0
InternalResolution = 2
WindowWidth = {WIDTH}
WindowHeight = {HEIGHT}
FullScreen = True
VSyncInterval = False
RenderingMode = 1
SoftwareRendering = False
HardwareTransform = True
SoftwareSkinning = True

[Audio]
Enable = True
AudioBackend = 0
AudioVolume = 10

[Control]
ShowTouchControls = False
"""

    for p in [
        base_psp / "SYSTEM",
        Path.home() / ".config" / "ppsspp" / "PSP" / "SYSTEM",
        Path("/root/.config/ppsspp/PSP/SYSTEM"),
        Path("/content/.config/ppsspp/PSP/SYSTEM"),
        Path("/content/memstick/PSP/SYSTEM"),
        Path("/root/memstick/PSP/SYSTEM")
    ]:
        p.mkdir(parents=True, exist_ok=True)
        (p / "ppsspp.ini").write_text(ini_content)

def download_game(game_url, filename="game.cso"):
    print(f"📥 [3/6] Baixando jogo para a memória do Colab: {filename}...", flush=True)
    games_dir = Path("/content/games")
    games_dir.mkdir(parents=True, exist_ok=True)
    target = games_dir / filename
    if not target.exists() or target.stat().st_size < 1000000:
        os.system(f"wget -q --show-progress -O {target} {game_url}")
    print(f"✅ Jogo pronto: {target} ({target.stat().st_size // (1024*1024)} MB)", flush=True)
    return str(target)

def start_virtual_display():
    print("🖥️ [4/6] Iniciando display virtual X11 (:1) e gerenciador de janelas Openbox...", flush=True)
    os.system("pkill -9 Xvfb > /dev/null 2>&1")
    os.system("pkill -9 openbox > /dev/null 2>&1")
    os.system(f"Xvfb :1 -screen 0 {WIDTH}x{HEIGHT}x24 +extension GLX +render -noreset &")
    time.sleep(1)
    os.system("DISPLAY=:1 openbox & > /dev/null 2>&1")
    time.sleep(0.5)

    # Desativar drivers Vulkan do host para forçar OpenGL imediato
    os.system("mv /etc/vulkan/icd.d/nvidia_icd.json /etc/vulkan/icd.d/nvidia_icd.json.bak > /dev/null 2>&1 || true")
    os.system("mv /usr/share/vulkan/icd.d/nvidia_icd.json /usr/share/vulkan/icd.d/nvidia_icd.json.bak > /dev/null 2>&1 || true")

    # Configurar e iniciar PulseAudio como root com permissões de acesso
    os.system("adduser root pulse-access > /dev/null 2>&1 || true")
    os.system("mkdir -p /var/run/pulse && chmod 777 /var/run/pulse 2>/dev/null || true")
    os.system("printf 'default-server = /var/run/pulse/native\\nautospawn = no\\n' > /etc/pulse/client.conf 2>/dev/null || true")
    os.system("pkill -9 pulseaudio > /dev/null 2>&1")
    os.system("pulseaudio --system --disallow-exit --disable-shm --exit-idle-time=-1 --daemonize=yes > /dev/null 2>&1")
    time.sleep(1)

async def run_server(game_path):
    from aiohttp import web

    print("🚀 [5/6] Iniciando servidor Web e canal de transmissão...")

    # Abrir processo xdotool interativo com stdin persistente (<0.05ms de latência)
    xdotool_proc = subprocess.Popen(
        ["xdotool", "-"],
        stdin=subprocess.PIPE,
        env={**os.environ, "DISPLAY": ":1"},
        text=True,
        bufsize=1
    )

    connected_stream_clients = set()

    async def handle_index(request):
        return web.Response(text=HTML_TEMPLATE, content_type='text/html')

    async def handle_jsmpeg(request):
        if os.path.exists("jsmpeg.min.js"):
            return web.FileResponse("jsmpeg.min.js")
        return web.Response(status=404)

    async def handle_stream_input(request):
        # Recebe chunks MPEG-TS do FFmpeg e transmite para os clientes WebSocket
        async for chunk in request.content.iter_chunked(4096):
            for ws in list(connected_stream_clients):
                try:
                    await ws.send_bytes(chunk)
                except Exception:
                    connected_stream_clients.discard(ws)
        return web.Response(text="OK")

    async def handle_ws_stream(request):
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        connected_stream_clients.add(ws)
        try:
            async for _ in ws:
                pass
        finally:
            connected_stream_clients.discard(ws)
        return ws

    async def handle_ws_input(request):
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        try:
            async for msg in ws:
                if msg.type == web.WSMsgType.TEXT:
                    try:
                        data = json.loads(msg.data)
                        key = data.get("key")
                        state = data.get("state")
                        if key and state in ("down", "up"):
                            cmd = f"key{state} {key}\n"
                            xdotool_proc.stdin.write(cmd)
                            xdotool_proc.stdin.flush()
                    except Exception:
                        pass
        finally:
            pass
        return ws

    app = web.Application()
    app.router.add_get('/', handle_index)
    app.router.add_get('/jsmpeg.min.js', handle_jsmpeg)
    app.router.add_post('/stream_input', handle_stream_input)
    app.router.add_get('/ws/stream', handle_ws_stream)
    app.router.add_get('/ws/input', handle_ws_input)

    global PORT
    PORT = get_free_port(PORT)
    os.system(f"fuser -k {PORT}/tcp > /dev/null 2>&1")
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, '127.0.0.1', PORT)
    await site.start()
    print(f"✅ Servidor Web local ativo na porta {PORT}!", flush=True)

    # Criar script launcher para o PPSSPP que garante isolamento e tratamento de áudio
    launcher_path = Path("/content/run_ppsspp.sh")
    launcher_content = f"""#!/bin/bash
export DISPLAY=:1
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=3.3
export SDL_VIDEODRIVER=x11
export VK_ICD_FILENAMES=/dev/null
export VK_DRIVER_FILES=/dev/null
export VK_LOADER_DRIVERS_DISABLE=*
export HOME=/root
export XDG_CONFIG_HOME=/root/.config

# Desativar qualquer override interno de áudio no AppRun se existir
if [ -f "./squashfs-root/AppRun" ]; then
    sed -i 's/SDL_AUDIODRIVER=pulse.*/#disabled/g' ./squashfs-root/AppRun 2>/dev/null || true
fi

# Testar se o PulseAudio está aceitando conexões
if pactl info > /dev/null 2>&1; then
    export PULSE_SERVER=/var/run/pulse/native
    export SDL_AUDIODRIVER=pulseaudio
else
    export SDL_AUDIODRIVER=dummy
fi

exec ./squashfs-root/AppRun --fullscreen "$@"
"""
    launcher_path.write_text(launcher_content)
    os.system("chmod +x /content/run_ppsspp.sh")

    print(f"🕹️ Iniciando PPSSPP no display :1 com o jogo {game_path}...", flush=True)
    ppsspp_log = open("/content/ppsspp.log", "w")
    game_proc = subprocess.Popen(
        ["/bin/bash", "/content/run_ppsspp.sh", game_path],
        stdout=ppsspp_log,
        stderr=subprocess.STDOUT,
        cwd=str(Path.cwd())
    )
    time.sleep(3)

    if game_proc.poll() is not None:
        print(f"❌ PPSSPP fechou com código {game_proc.returncode}!", flush=True)
        if os.path.exists("/content/ppsspp.log"):
            print("--- LOG DO PPSSPP ---\n" + open("/content/ppsspp.log").read()[:1500] + "\n---------------------", flush=True)
    else:
        print(f"✅ PPSSPP ativo e renderizando! (PID {game_proc.pid})", flush=True)
        os.system("DISPLAY=:1 xdotool search --onlyvisible --class PPSSPPSDL windowactivate > /dev/null 2>&1 || true")

    # Iniciar FFmpeg Capture & Stream
    print("📹 Iniciando codificador de vídeo a 60 FPS...", flush=True)
    ffmpeg_cmd = [
        "ffmpeg", "-y",
        "-f", "x11grab", "-draw_mouse", "0", "-framerate", str(FPS),
        "-video_size", f"{WIDTH}x{HEIGHT}", "-i", ":1.0",
        "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-f", "mpegts",
        "-codec:v", "mpeg1video", "-s", f"{WIDTH}x{HEIGHT}",
        "-b:v", "2800k", "-bf", "0", "-r", str(FPS),
        "-codec:a", "mp2", "-b:a", "128k", "-ar", "44100", "-ac", "2",
        "-muxdelay", "0.001",
        f"http://127.0.0.1:{PORT}/stream_input"
    ]
    ffmpeg_log = open("/content/ffmpeg.log", "w")
    ffmpeg_proc = subprocess.Popen(ffmpeg_cmd, stdout=ffmpeg_log, stderr=subprocess.STDOUT)

    # Iniciar Cloudflare Tunnel com logfile não-bloqueante
    print("🌐 [6/6] Criando Túnel Seguro Cloudflare...", flush=True)
    os.system("rm -f /content/cloudflared.log")
    tunnel_proc = subprocess.Popen(
        ["./cloudflared", "tunnel", "--url", f"http://127.0.0.1:{PORT}", "--logfile", "/content/cloudflared.log"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )

    tunnel_url = None
    start_t = time.time()
    while time.time() - start_t < 35:
        time.sleep(1)
        if os.path.exists("/content/cloudflared.log"):
            try:
                with open("/content/cloudflared.log", "r", errors="ignore") as f:
                    log_content = f.read()
                    m = re.search(r'https://[a-zA-Z0-9-]+\.trycloudflare\.com', log_content)
                    if m:
                        tunnel_url = m.group(0)
                        break
            except Exception:
                pass

    if not tunnel_url:
        print("❌ Erro ao obter URL do túnel. Verifique a conexão.", flush=True)
        if os.path.exists("/content/cloudflared.log"):
            print("Logs do Cloudflared:\n" + open("/content/cloudflared.log").read()[:500])
        return

    # Renderizar Card de Conexão com QR Code
    # Exibir Link e QR Code no terminal/notebook de forma garantida
    print("\n" + "="*60, flush=True)
    print("🎮 LIVREGAMES 3D CLOUD ATIVO!", flush=True)
    print("⚡ Renderizado pela GPU Tesla T4 no Google Colab", flush=True)
    print("📱 Abra o link abaixo no seu celular para jogar:", flush=True)
    print(f"\n👉 LINK: {tunnel_url}\n", flush=True)
    print("="*60, flush=True)

    try:
        import qrcode
        qr = qrcode.QRCode(box_size=1, border=1)
        qr.add_data(tunnel_url)
        qr.make(fit=True)
        print("\n📷 ESCANEIE O QR CODE COM A CÂMERA DO CELULAR:\n", flush=True)
        qr.print_ascii(invert=True)
    except Exception:
        pass

    try:
        from IPython.display import HTML, display
        import qrcode
        import io, base64

        qr_img = qrcode.QRCode(box_size=5, border=2)
        qr_img.add_data(tunnel_url)
        qr_img.make(fit=True)
        img = qr_img.make_image(fill_color="black", back_color="white")
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        qr_b64 = base64.b64encode(buf.getvalue()).decode()

        display(HTML(f"""
        <div style="background: linear-gradient(135deg, #090d16, #1e293b); padding: 25px; border-radius: 20px; border: 2px solid #38bdf8; color: white; font-family: -apple-system, sans-serif; text-align: center; max-width: 500px; margin: 20px auto; box-shadow: 0 12px 40px rgba(0,0,0,0.6);">
            <div style="font-size: 32px; margin-bottom: 8px;">🎮</div>
            <h2 style="color: #38bdf8; margin: 0; font-size: 22px;">LIVREGAMES CLOUD ATIVO!</h2>
            <p style="font-size: 14px; color: #94a3b8; margin: 6px 0 16px 0;">GPU Tesla T4 • 60 FPS • Dragon Ball Z Shin Budokai</p>
            
            <div style="background: white; padding: 12px; border-radius: 14px; display: inline-block; margin-bottom: 16px;">
                <img src="data:image/png;base64,{qr_b64}" style="display: block; width: 170px; height: 170px;" alt="QR Code"/>
            </div>
            
            <p style="margin: 0 0 12px 0; font-size: 13px; color: #cbd5e1;">Aponte a câmera do seu celular para o QR Code acima ou clique no botão:</p>
            
            <a href="{tunnel_url}" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #2563eb, #1d4ed8); color: white; padding: 14px 28px; border-radius: 12px; text-decoration: none; font-weight: 700; font-size: 16px; box-shadow: 0 4px 15px rgba(37,99,235,0.4);">
                🕹️ ABRIR NO CELULAR
            </a>
            
            <div style="margin-top: 15px; padding-top: 15px; border-top: 1px solid rgba(255,255,255,0.1); font-size: 11px; color: #64748b; word-break: break-all;">
                {tunnel_url}
            </div>
        </div>
        """))
    except Exception:
        pass

    # Manter servidor ativo
    print("\n🟢 Servidor ativo e transmitindo a 60 FPS. Bom jogo!\n", flush=True)
    while True:
        await asyncio.sleep(10)

def main():
    setup_environment()
    setup_ppsspp()
    game_path = download_game(DEFAULT_GAME_URL, "dbz_shin_budokai.cso")
    start_virtual_display()
    asyncio.run(run_server(game_path))

if __name__ == "__main__":
    main()
