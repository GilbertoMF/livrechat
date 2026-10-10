# 📱 LivreChat — Mensageiro Privado sem Número de Telefone

O **LivreChat** é uma plataforma completa e moderna de mensagens instantâneas e chamadas de voz de alta qualidade, inspirada no WhatsApp, porém **sem exigir número de telefone, chip telefônico ou SMS para cadastro**.

O projeto conta com:
- **Aplicativo Android Nativo**: Desenvolvido em Kotlin com Jetpack Compose Material 3, Room Database, OkHttp/Retrofit, WebRTC nativo (Stream WebRTC) e Criptografia AES-256-GCM.
- **Backend Completo**: Desenvolvido em Node.js com TypeScript, WebSocket em tempo real, suporte total a PostgreSQL e armazenamento local zero-setup para desenvolvimento imediato.
- **APK Compilado**: Arquivo pronto para instalação gerado na raiz do projeto (`LivreChat-debug.apk`).

---

## 🏗️ Estrutura do Projeto

```
LivreChat/
├── LivreChat-debug.apk         # APK compilado e pronto para instalação
├── README.md                  # Este guia completo de instalação e operação
├── server/                    # Backend Node.js + TypeScript
│   ├── src/
│   │   ├── config/env.ts      # Variáveis de ambiente e configuração STUN/TURN
│   │   ├── db/database.ts     # Abstração de banco (PostgreSQL + Local Store)
│   │   ├── middleware/auth.ts # Validação JWT e Rate Limiting
│   │   ├── modules/
│   │   │   ├── auth/          # Cadastro sem telefone, login, chave de recuperação
│   │   │   ├── users/         # Perfis, QR Code, contatos, bloqueios e denúncias
│   │   │   ├── messages/      # Histórico, paginação, ticks de leitura
│   │   │   └── calls/         # Histórico de chamadas e ICE servers
│   │   ├── websocket/ws.server.ts # Chat em tempo real e sinalização WebRTC
│   │   └── index.ts           # Inicialização do servidor HTTP e WebSocket
│   ├── test/                  # Testes automatizados (Node test runner)
│   ├── schema.sql             # Esquema DDL para PostgreSQL
│   ├── package.json
│   └── tsconfig.json
└── android/                   # Aplicativo Android em Kotlin + Jetpack Compose
    ├── app/
    │   ├── src/main/
    │   │   ├── java/com/livrechat/app/
    │   │   │   ├── crypto/CryptoManager.kt       # E2E e criptografia de backup (AES-GCM)
    │   │   │   ├── data/
    │   │   │   │   ├── local/                    # Room DB (Entities, DAOs, EncryptedPrefs)
    │   │   │   │   └── remote/                   # Retrofit, ApiClient, WebSocketClient
    │   │   │   ├── webrtc/WebRTCManager.kt       # Chamadas de voz P2P de baixa latência
    │   │   │   ├── service/CallForegroundService.kt # Áudio em segundo plano/notificação
    │   │   │   ├── ui/
    │   │   │   │   ├── theme/                    # Tema Material 3 com azul primário
    │   │   │   │   ├── auth/                     # Welcome, Login, Register (12 palavras), Recovery
    │   │   │   │   ├── home/                     # Conversas, Chamadas, Badges
    │   │   │   │   ├── chat/                     # Tela de chat, bolhas, ticks de leitura
    │   │   │   │   ├── call/                     # Tela ativa e tela de recebimento
    │   │   │   │   ├── contacts/                 # Busca por username, tela de QR Code
    │   │   │   │   └── settings/                 # Backup Google Drive criptografado
    │   │   │   ├── LivreChatApp.kt               # Classe Application e Canais de Notificação
    │   │   │   └── MainActivity.kt               # Permissões em tempo de execução
    │   │   └── AndroidManifest.xml
    │   └── build.gradle.kts
    └── build.gradle.kts
```

---

## 🚀 Como Executar o Backend Localmente

### 1. Pré-requisitos
- Node.js 18+ instalado.

### 2. Instalação e Execução
No terminal:
```powershell
cd C:\Users\Gilberto\.gemini\antigravity\scratch\LivreChat\server
npm install
npm run dev
```

O servidor iniciará:
- **HTTP REST API**: `http://localhost:3000`
- **WebSocket**: `ws://localhost:3000/ws`
- **Health Check**: `http://localhost:3000/health`

### 3. Executando os Testes Automatizados
```powershell
cd C:\Users\Gilberto\.gemini\antigravity\scratch\LivreChat\server
npm test
```
*Executa os testes de cadastro sem número, login, redefinição por chave mnemônica, troca de mensagens e controle de acesso.*

---

## 🗄️ Banco de Dados: PostgreSQL & Modo Zero-Setup

1. **Modo Zero-Setup (Padrão para Desenvolvimento)**:
   - Se nenhuma variável `DATABASE_URL` for definida, o servidor salva os dados em `server/data/livrechat.db.json` de forma atômica e persistente.
   - Não requer Docker nem instalação de banco para começar a testar.

2. **Modo PostgreSQL (Produção ou Testes com Postgres)**:
   - Configure o arquivo `.env`:
     ```env
     DATABASE_URL=postgresql://usuario:senha@localhost:5432/livrechat
     ```
   - O servidor migra automaticamente as tabelas definidas em `schema.sql` na inicialização.

---

## 🎙️ Chamadas de Voz WebRTC (Baixa Latência)

O aplicativo utiliza a biblioteca nativa do WebRTC (`io.getstream:stream-webrtc-android`), garantindo:
- Codec de áudio Opus com compressão adaptativa.
- Cancelamento de eco por hardware (AEC) e supressão de ruído ativados nativamente.
- Sinalização em tempo real via WebSocket (`call:offer`, `call:answer`, `call:ice_candidate`, `call:ringing`, `call:reject`, `call:hangup`).
- Servidores STUN públicos da Google já integrados (`stun:stun.l.google.com:19302`).
- Suporte a servidores TURN configuráveis no backend (`TURN_URL`, `TURN_USERNAME`, `TURN_PASSWORD`).
- **Foreground Service**: Mantém a chamada ativa quando o usuário sai do app ou bloqueia a tela, com notificação e botão de desligar.

---

## 🔒 Criptografia e Backup Opcional no Google Drive

1. **Criptografia de Mensagens**:
   - Mensagens protegidas com `AES-256-GCM` com vetores de inicialização (IV) únicos de 96 bits.
   - Nenhum algoritmo proprietário inventado.

2. **Backup no Google Drive**:
   - Fica na aba **Configurações > Backup e Restauração**.
   - Totalmente opcional (o mensageiro funciona 100% sem conta Google).
   - O arquivo `.enc` é derivado de uma senha informada pelo usuário com `PBKDF2WithHmacSHA256` (100.000 iterações) e `AES-GCM`.
   - Inclui checksum SHA-256 para verificar que o arquivo não foi corrompido antes da restauração.

---

## 📲 Como Instalar e Testar o Aplicativo Android

### 1. Instalar o APK no Aparelho ou Emulador
O APK já compilado está em:
```
C:\Users\Gilberto\.gemini\antigravity\scratch\LivreChat\LivreChat-debug.apk
```

Para instalar via cabo USB (ADB):
```powershell
adb install -r "C:\Users\Gilberto\.gemini\antigravity\scratch\LivreChat\LivreChat-debug.apk"
```

### 2. Testando entre Dois Aparelhos na Mesma Rede Wi-Fi
1. Descubra o IP do computador na rede local (ex: `192.168.1.100` via `ipconfig`).
2. Abra o LivreChat no aparelho e, na tela de Login/Cadastro, toque no ícone de engrenagem no topo para apontar o servidor para `http://192.168.1.100:3000`.
3. No Aparelho 1: Cadastre o usuário `alice`. Guarde a chave de 12 palavras exibida.
4. No Aparelho 2: Cadastre o usuário `bob`.
5. No Aparelho 1: Toque na busca, digite `bob` e envie uma mensagem.
6. A conversa abrirá em tempo real nos dois celulares com ticks de envio e entrega.
7. Toque no ícone de telefone para iniciar a chamada WebRTC e testar a voz com viva-voz e microfone.

---

## 🛠️ Como Recompilar o APK
Se fizer alterações no código Kotlin:
```powershell
cd C:\Users\Gilberto\.gemini\antigravity\scratch\LivreChat\android
.\gradlew.bat assembleDebug
```
O novo APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
