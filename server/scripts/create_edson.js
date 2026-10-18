const { Pool } = require('pg');
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');

const pool = new Pool({
  connectionString: 'postgresql://livrechat:livrechat_secure_pass_2026@127.0.0.1:5432/livrechat'
});

async function run() {
  const edsonId = uuidv4();
  const edsonUsername = '74918230';
  const edsonPassword = '123456';
  const edsonDisplayName = 'Edson Martins peronico';
  const gilbertoId = '3b3adc6b-45de-4473-b9e5-896c6a03553c';

  const passwordHash = await bcrypt.hash(edsonPassword, 10);
  const recoveryHash = await bcrypt.hash('sereno horizonte sol terra nuvem flor estrela pedra ouro prata rio lua', 10);

  // 1. Insert user
  await pool.query(
    'INSERT INTO users (id, username, password_hash, recovery_hash) VALUES ($1, $2, $3, $4)',
    [edsonId, edsonUsername, passwordHash, recoveryHash]
  );
  console.log('User created:', edsonUsername, edsonId);

  // 2. Insert profile
  await pool.query(
    'INSERT INTO user_profiles (user_id, display_name, bio) VALUES ($1, $2, $3)',
    [edsonId, edsonDisplayName, 'Disponível no LivreChat']
  );
  console.log('Profile created:', edsonDisplayName);

  // 3. Insert preferences
  await pool.query(
    'INSERT INTO user_preferences (user_id) VALUES ($1) ON CONFLICT DO NOTHING',
    [edsonId]
  );

  // 4. Create mutual contacts
  await pool.query(
    'INSERT INTO contacts (id, user_id, contact_user_id, nickname) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING',
    ['cont_' + uuidv4().substring(0, 8), gilbertoId, edsonId, 'Edson Martins']
  );
  await pool.query(
    'INSERT INTO contacts (id, user_id, contact_user_id, nickname) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING',
    ['cont_' + uuidv4().substring(0, 8), edsonId, gilbertoId, 'Gilberto Martins']
  );
  console.log('Mutual contacts created!');

  // 5. Create direct conversation
  const convId = 'conv_' + Date.now() + '_edson_gilberto';
  await pool.query(
    'INSERT INTO conversations (id, is_group, created_by) VALUES ($1, false, $2) ON CONFLICT DO NOTHING',
    [convId, gilbertoId]
  );
  await pool.query(
    'INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2), ($1, $3) ON CONFLICT DO NOTHING',
    [convId, gilbertoId, edsonId]
  );
  console.log('Conversation created:', convId);

  await pool.end();
  console.log('SUCCESS! Edson account ready with ID: ' + edsonUsername + ' and password: ' + edsonPassword);
}

run().catch(e => {
  console.error(e);
  process.exit(1);
});
