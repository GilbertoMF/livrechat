# 🚀 Guia Passo a Passo: Deploy 100% Grátis sem Cartão de Crédito

Este guia explica exatamente como colocar o backend do **LivreChat** online na nuvem, funcionando 24h por dia sem dormir, sem delay e **sem precisar de cartão de crédito**.

---

## 📋 Resumo dos 3 Serviços Gratuitos Utilizados:
1. **Neon.tech**: Banco de dados PostgreSQL (sem cartão, cria em 10 segundos).
2. **Render.com**: Hospedagem do servidor Node.js + WebSocket (sem cartão).
3. **UptimeRobot**: Robô gratuito que faz um ping a cada 5 minutos na rota `/health`, impedindo o Render de hibernar (zero delay para os usuários).

---

## 🛠️ Passo 1: Criar o Banco PostgreSQL no Neon.tech (1 minuto)

1. Acesse: **[https://neon.tech](https://neon.tech)**
2. Clique em **"Sign Up"** e faça login com sua conta do **GitHub** ou **Google** (não pede cartão).
3. Na tela inicial, dê um nome ao projeto (ex: `livrechat-db`) e clique em **"Create Project"**.
4. Ele exibirá imediatamente uma tela com a **Connection String**.
5. Mude a caixinha de seleção para **"PostgreSQL"** e copie a URL que começa com `postgresql://...`
   - Exemplo:
     ```
     postgresql://neondb_owner:senha123@ep-cool-cloud-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
     ```
6. Guarde essa URL para o próximo passo!

---

## 🌐 Passo 2: Subir o Backend no Render.com (2 minutos)

1. Se você ainda não colocou o código no GitHub:
   - Crie um repositório no seu GitHub (pode ser público ou privado) chamado `livrechat`.
   - Suba a pasta `server` (ou o projeto todo) para lá.
2. Acesse: **[https://render.com](https://render.com)**
3. Faça login com seu **GitHub** (não pede cartão).
4. No painel do Render, clique em **"New +"** no canto superior e selecione **"Web Service"**.
5. Selecione o repositório do seu GitHub.
6. Preencha os campos:
   - **Name:** `livrechat` (ou o nome que preferir)
   - **Root Directory:** `server` (caso a pasta server esteja na raiz do repositório)
   - **Runtime:** `Node`
   - **Build Command:** `npm install && npm run build`
   - **Start Command:** `npm start`
   - **Instance Type:** `Free`
7. Role até a seção **"Environment Variables"** (Variáveis de Ambiente) e adicione:
   - `NODE_ENV`: `production`
   - `JWT_SECRET`: `sua_chave_super_secreta_qualquer_coisa_longa`
   - `DATABASE_URL`: *(Cole a URL do Neon.tech que você copiou no Passo 1)*
8. Clique em **"Deploy Web Service"**.
9. Aguarde 2 minutos. O Render vai compilar o código e exibir:
   ```
   ==> Your service is live at https://seu-app.onrender.com
   ```
   *(Copie essa URL!)*

---

## ⏰ Passo 3: Impedir o Render de Dormir com UptimeRobot (1 minuto)

O plano gratuito do Render hiberna após 15 minutos sem visitas. Para que ele **NUNCA mais durma** e responda instantaneamente a qualquer hora:

1. Acesse: **[https://uptimerobot.com](https://uptimerobot.com)**
2. Crie sua conta grátis clicando em **"Register for FREE"** (não pede cartão).
3. No painel, clique no botão **"+ Add New Monitor"**.
4. Configure assim:
   - **Monitor Type:** `HTTP(s)`
   - **Friendly Name:** `LivreChat Ping`
   - **URL (or IP):** `https://seu-app.onrender.com/health` *(substitua pela sua URL do Render seguida de /health)*
   - **Monitoring Interval:** `5 minutes` (ou `10 minutes`)
5. Clique em **"Create Monitor"**.

🎉 **Pronto!** A cada 5 minutos o UptimeRobot visita a rota `/health`. O Render entende que o servidor está sempre ativo, mantendo os WebSockets e as chamadas de voz prontas sem nenhum segundo de delay!

---

## 📱 Passo 4: Conectar o Aplicativo Android ao Servidor Online

Agora que seu servidor está 100% online na nuvem:

1. Abra o **LivreChat** no seu celular Android.
2. Na tela inicial de **Login**, toque no **ícone de engrenagem ⚙️** no canto superior direito.
3. Substitua o endereço local pelo seu link do Render:
   ```
   https://seu-app.onrender.com
   ```
4. Pronto! Qualquer pessoa com o aplicativo instalado agora poderá se cadastrar, trocar mensagens e fazer chamadas de voz de qualquer lugar do mundo, seja no Wi-Fi ou no 4G/5G!
