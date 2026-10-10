const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

async function runMigration() {
  console.log('Conectando ao banco Neon.tech em São Paulo...');
  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
  });

  try {
    const client = await pool.connect();
    console.log('✅ Conexão estabelecida com sucesso!');

    const schemaSql = fs.readFileSync(path.join(__dirname, '../schema.sql'), 'utf-8');
    console.log('Executando migração de schema (16 tabelas)...');
    await client.query(schemaSql);
    console.log('✅ Todas as tabelas criadas com sucesso no Neon.tech!');

    const res = await client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public' 
      ORDER BY table_name;
    `);

    console.log('\nTabelas no banco:');
    res.rows.forEach(r => console.log(' - ' + r.table_name));

    client.release();
    await pool.end();
    console.log('\nMigração finalizada com êxito!');
  } catch (err) {
    console.error('❌ Erro na migração:', err);
    process.exit(1);
  }
}

runMigration();
