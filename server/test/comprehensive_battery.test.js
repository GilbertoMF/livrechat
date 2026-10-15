const test = require('node:test');
const assert = require('node:assert');

const { db } = require('../dist/db/database');
const { AuthService, generateRecoveryKey } = require('../dist/modules/auth/auth.service');

test('LivreChat Comprehensive System & Feature Test Battery', async (t) => {
  const ts = Date.now();
  const user1Name = `test_user1_${ts}`;
  const user2Name = `test_user2_${ts}`;
  const user3Name = `test_user3_${ts}`;

  let u1, u2, u3;

  // 1. AUTH & RECOVERY TESTS
  await t.test('1. Mnemonic Recovery Key generation produces 12 valid words', () => {
    const key = generateRecoveryKey();
    const words = key.split(' ');
    assert.strictEqual(words.length, 12);
    words.forEach(w => assert.ok(w.length > 0));
  });

  await t.test('2. Register User 1 without phone number', async () => {
    u1 = await AuthService.register({
      username: user1Name,
      password: 'password_user1',
      displayName: 'Carlos Eduardo'
    });
    assert.ok(u1.user.id);
    assert.strictEqual(u1.user.username, user1Name);
    assert.strictEqual(u1.user.displayName, 'Carlos Eduardo');
    assert.ok(u1.recoveryKey);
    assert.ok(u1.tokens.accessToken);
  });

  await t.test('3. Register User 2 and User 3', async () => {
    u2 = await AuthService.register({
      username: user2Name,
      password: 'password_user2',
      displayName: 'Mariana Lima'
    });
    u3 = await AuthService.register({
      username: user3Name,
      password: 'password_user3',
      displayName: 'Lucas Santos'
    });
    assert.ok(u2.user.id);
    assert.ok(u3.user.id);
  });

  await t.test('4. Login authentication works and rejects wrong credentials', async () => {
    const valid = await AuthService.login({ username: user1Name, password: 'password_user1' });
    assert.ok(valid.tokens.accessToken);

    await assert.rejects(
      async () => await AuthService.login({ username: user1Name, password: 'wrong_password' }),
      /incorretos/
    );
  });

  await t.test('5. Prevent duplicate username registration', async () => {
    await assert.rejects(
      async () => await AuthService.register({ username: user1Name, password: 'password123', displayName: 'Cloned' }),
      /já está em uso/
    );
  });

  await t.test('6. Profile Management & Search', async () => {
    // Update profile
    const profile = await db.upsertProfile({
      user_id: u1.user.id,
      display_name: 'Carlos Eduardo Atualizado',
      bio: 'Desenvolvedor LivreChat',
      avatar_url: 'https://example.com/avatar1.png',
      updated_at: new Date().toISOString()
    });
    assert.strictEqual(profile.display_name, 'Carlos Eduardo Atualizado');
    assert.strictEqual(profile.bio, 'Desenvolvedor LivreChat');

    // Fetch updated profile
    const fetched = await db.getProfile(u1.user.id);
    assert.strictEqual(fetched?.display_name, 'Carlos Eduardo Atualizado');

    // Search by username
    const foundUser = await db.findUserByUsername(user1Name);
    assert.ok(foundUser);
    assert.strictEqual(foundUser.id, u1.user.id);
  });

  // 2. CONTACTS MANAGEMENT TESTS
  await t.test('7. Add, Edit Nickname and Remove Contacts', async () => {
    // U1 adds U2
    const contact = await db.addContact({
      id: `cnt_${ts}_1`,
      user_id: u1.user.id,
      contact_user_id: u2.user.id,
      nickname: 'Mari Amiga',
      created_at: new Date().toISOString()
    });
    assert.ok(contact.id);

    // List contacts
    let contacts = await db.getContacts(u1.user.id);
    const foundContact = contacts.find(c => c.contact_user_id === u2.user.id);
    assert.ok(foundContact);
    assert.strictEqual(foundContact.nickname, 'Mari Amiga');

    // Update contact nickname
    await db.updateContactNickname(u1.user.id, u2.user.id, 'Mariana VIP');
    contacts = await db.getContacts(u1.user.id);
    const updatedContact = contacts.find(c => c.contact_user_id === u2.user.id);
    assert.strictEqual(updatedContact.nickname, 'Mariana VIP');

    // Remove contact
    await db.removeContact(u1.user.id, u2.user.id);
    contacts = await db.getContacts(u1.user.id);
    assert.strictEqual(contacts.some(c => c.contact_user_id === u2.user.id), false);
  });

  // 3. GROUP CHAT SYSTEM TESTS
  let groupId;
  await t.test('8. Group Creation, Members, and Discovery', async () => {
    const groupConv = await db.createGroupConversation(
      'Grupo dos Devs',
      u1.user.id,
      [u2.user.id, u3.user.id],
      'https://example.com/group_avatar.png'
    );
    assert.ok(groupConv.id);
    assert.strictEqual(groupConv.is_group, true);
    assert.strictEqual(groupConv.title, 'Grupo dos Devs');
    groupId = groupConv.id;

    // Check members
    const members = await db.getConversationMembers(groupId);
    assert.strictEqual(members.length, 3);
    assert.ok(members.includes(u1.user.id));
    assert.ok(members.includes(u2.user.id));
    assert.ok(members.includes(u3.user.id));

    // Verify conversations list for member U2
    const u2Convs = await db.getConversationsForUser(u2.user.id);
    const g = u2Convs.find(c => c.conversationId === groupId);
    assert.ok(g, 'Group conversation must be listed for member');
    assert.strictEqual(g.isGroup, true);
    assert.strictEqual(g.title, 'Grupo dos Devs');
  });

  // 4. MESSAGING (1:1 & GROUP), REACTIONS, RECEIPTS, AND DELETION
  await t.test('9. Send, Receive, React, and Delete Messages', async () => {
    // Self conversation ("Mensagens para mim")
    const selfConvId = await db.getOrCreateDirectConversation(u1.user.id, u1.user.id);
    assert.ok(selfConvId);
    const sameSelfConvId = await db.getOrCreateDirectConversation(u1.user.id, u1.user.id);
    assert.strictEqual(selfConvId, sameSelfConvId);

    // 1:1 conversation
    const directConvId = await db.getOrCreateDirectConversation(u1.user.id, u2.user.id);
    assert.ok(directConvId);

    // Send 1:1 message
    const msg1 = await db.createMessage({
      id: `msg_direct_${ts}`,
      conversation_id: directConvId,
      sender_id: u1.user.id,
      content: 'Mensagem direta de teste',
      message_type: 'text',
      is_deleted: false,
      is_edited: false,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    });
    assert.strictEqual(msg1.content, 'Mensagem direta de teste');

    // Send group message
    const groupMsg = await db.createMessage({
      id: `msg_grp_${ts}`,
      conversation_id: groupId,
      sender_id: u3.user.id,
      content: 'Olá a todos no grupo!',
      message_type: 'text',
      is_deleted: false,
      is_edited: false,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    });
    assert.strictEqual(groupMsg.content, 'Olá a todos no grupo!');

    // Get messages for group
    const grpMessages = await db.getMessages(groupId);
    assert.ok(grpMessages.some(m => m.id === groupMsg.id));

    // Add reaction
    const reactions = await db.addOrToggleReaction(groupMsg.id, u1.user.id, '❤️');
    assert.ok(reactions.includes('❤️'));

    // Soft delete message
    const deleted = await db.deleteMessage(msg1.id, u1.user.id);
    assert.strictEqual(deleted, true);

    const checkMsg = await db.getMessageById(msg1.id);
    assert.strictEqual(checkMsg?.is_deleted, true);
  });

  // 5. STATUS / STORIES TESTS
  await t.test('10. Status Stories Publishing, Querying, and Deletion', async () => {
    const status = await db.createStatus({
      id: `status_${ts}`,
      user_id: u1.user.id,
      username: user1Name,
      display_name: 'Carlos Eduardo',
      content_type: 'text',
      content: 'Meu status de teste no LivreChat!',
      background_color: '#4CAF50',
      created_at: new Date().toISOString(),
      expires_at: new Date(Date.now() + 86400000).toISOString()
    });
    assert.ok(status.id);

    const activeStatuses = await db.getActiveStatuses(u1.user.id);
    assert.ok(activeStatuses.length >= 1);
    const foundStatus = activeStatuses.find(s => s.id === status.id);
    assert.ok(foundStatus);

    await db.deleteStatus(status.id, u1.user.id);
    const afterDelete = await db.getActiveStatuses(u1.user.id);
    assert.strictEqual(afterDelete.some(s => s.id === status.id), false);
  });

  // 6. BLOCKING & PRIVACY TESTS
  await t.test('11. Block and Unblock Users', async () => {
    // U1 blocks U3
    await db.blockUser(u1.user.id, u3.user.id);
    let isBlocked = await db.isBlocked(u1.user.id, u3.user.id);
    assert.strictEqual(isBlocked, true);

    // Verify blocked list
    const blockedList = await db.getBlockedUsers(u1.user.id);
    assert.ok(blockedList.some(b => b.blocked_id === u3.user.id));

    // Unblock
    await db.unblockUser(u1.user.id, u3.user.id);
    isBlocked = await db.isBlocked(u1.user.id, u3.user.id);
    assert.strictEqual(isBlocked, false);
  });

  // 7. CALL HISTORY
  await t.test('12. Call History Recording and Querying', async () => {
    await db.recordCall({
      id: `call_${ts}`,
      caller_id: u1.user.id,
      callee_id: u2.user.id,
      status: 'completed',
      started_at: new Date().toISOString(),
      ended_at: new Date().toISOString(),
      duration_seconds: 45
    });

    const calls = await db.getCallHistory(u1.user.id);
    assert.ok(calls.some(c => c.id === `call_${ts}`));
  });
});
