# 🎮 LivreGames 3D Cloud Gaming (Google Colab GPU)

Transforme qualquer **Google Colab gratuito com GPU Tesla T4 (16 GB)** em um console de **Cloud Gaming 3D** de alta fidelidade (estilo xCloud / GeForce NOW).

---

## 🚀 Como Executar no Google Colab

### Passo 1: Abrir o Google Colab
Abra o Google Colab: [https://colab.research.google.com](https://colab.research.google.com)  
Garanta que a GPU está ativada:  
**Ambiente de execução (Runtime) > Alterar tipo de ambiente de execução > Acelerador de hardware: GPU T4**.

---

### Passo 2: Executar no Colab (Célula Única)
Crie uma nova célula no seu notebook e cole o seguinte comando:

```python
!wget -q -O colab_cloud_gaming.py https://pub-425fc23f99ae41f4bf6e29d5f0d07e12.r2.dev/cloud-gaming/colab_cloud_gaming.py
!python colab_cloud_gaming.py
```

Clique em **Play ▶️**.

---

### Passo 3: Conectar no Celular
1. O Colab irá exibir um **Card com QR Code** e um link público gerado pelo Cloudflare Tunnel (ex: `https://xxxx.trycloudflare.com`).
2. Aponte a câmera do seu celular para escanear o QR Code ou clique no link.
3. No celular, toque em **"TOQUE PARA JOGAR"** para desbloquear o áudio e a tela cheia.
4. Jogue usando os controles touch virtuais ou conecte um controle Bluetooth (Xbox, DualShock/DualSense ou Ipega)!

---

## 🕹️ Recursos do Servidor
- **Renderização por GPU Tesla T4:** 60 FPS com gráficos 3D nítidos em alta definição.
- **Transmissão Ultrarrápida:** Pipeline de vídeo MPEG-TS / WebSocket com latência inferior a 50ms.
- **Zero Download no Celular:** Nenhum arquivo pesado (ISO/CSO de 300MB a 1GB) é baixado no celular.
- **Multi-Touch Real:** Pressione múltiplos botões simultaneamente sem conflito.
- **Feedback Háptico:** Vibração tátil no celular a cada toque nos botões.
