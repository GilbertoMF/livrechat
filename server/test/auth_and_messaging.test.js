const test = require('node:test');
const assert = require('node:assert');

const { db } = require('../dist/db/database');
const { AuthService, generateRecoveryKey } = require('../dist/modules/auth/auth.service');

test('LivreChat Backend Core Functionality', async (t) => {
  await t.test('Recovery key generation creates 12 words', () => {
    const key = generateRecoveryKey();
    const words = key.split(' ');
    assert.strictEqual(words.length, 12, 'Must generate exactly 12 mnemonic words');
  });

  const ts = Date.now();
  const usernameA = `alice_${ts}`;
  const usernameB = `bob_${ts}`;
  let userA, userB;

  await t.test('Register user A without phone number', async () => {
    const res = await AuthService.register({
      username: usernameA,
      password: 'password123',
      displayName: 'Alice Silva'
    });

    assert.ok(res.user.id, 'User ID must be generated');
    assert.strictEqual(res.user.username, usernameA);
    assert.strictEqual(res.user.displayName, 'Alice Silva');
    assert.ok(res.recoveryKey, 'Recovery key must be generated');
    assert.ok(res.tokens.accessToken, 'Access token must be generated');
    userA = res;
  });

  await t.test('Register user B without phone number', async () => {
    const res = await AuthService.register({
      username: usernameB,
      password: 'password456',
      displayName: 'Bob Souza'
    });

    assert.ok(res.user.id);
    assert.strictEqual(res.user.username, usernameB);
    userB = res;
  });

  await t.test('Login user A with valid credentials', async () => {
    const res = await AuthService.login({
      username: usernameA,
      password: 'password123'
    });

    assert.strictEqual(res.user.username, usernameA);
    assert.ok(res.tokens.accessToken);
  });

  await t.test('Login with invalid password fails', async () => {
    await assert.rejects(
      async () => {
        await AuthService.login({
          username: usernameA,
          password: 'wrong_password'
        });
      },
      /incorretos/
    );
  });

  await t.test('Duplicate username registration is rejected', async () => {
    await assert.rejects(
      async () => {
        await AuthService.register({
          username: usernameA,
          password: 'anotherpassword',
          displayName: 'Impostor'
        });
      },
      /já está em uso/
    );
  });

  await t.test('Account recovery with valid mnemonic resets password', async () => {
    const recoveryRes = await AuthService.recoverAccount({
      username: usernameA,
      recoveryKey: userA.recoveryKey,
      newPassword: 'new_super_secure_password'
    });

    assert.strictEqual(recoveryRes.success, true);

    // Verify login with new password
    const loginRes = await AuthService.login({
      username: usernameA,
      password: 'new_super_secure_password'
    });
    assert.ok(loginRes.tokens.accessToken);
  });

  await t.test('Conversation creation and direct messaging', async () => {
    const convId = await db.getOrCreateDirectConversation(userA.user.id, userB.user.id);
    assert.ok(convId, 'Conversation ID should be created');

    const msg = await db.createMessage({
      id: `msg_test_${ts}`,
      conversation_id: convId,
      sender_id: userA.user.id,
      content: 'Olá Bob, este é o LivreChat!',
      message_type: 'text',
      is_deleted: false,
      is_edited: false,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    });

    assert.strictEqual(msg.id, `msg_test_${ts}`);

    const messages = await db.getMessages(convId);
    assert.ok(messages.length >= 1);
    assert.strictEqual(messages[messages.length - 1].content, 'Olá Bob, este é o LivreChat!');
  });
});
