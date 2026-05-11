import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { createPool, type Pool } from 'mysql2/promise';
import { drizzle } from 'drizzle-orm/mysql2';
import * as schema from './schema';
import type { AppDatabase } from './app-database';

@Injectable()
export class DrizzleProvider implements OnModuleDestroy {
  private readonly pool: Pool;
  readonly db: AppDatabase;

  constructor() {
    const host = process.env.MYSQL_HOST ?? 'localhost';
    const port = Number(process.env.MYSQL_PORT ?? 3306);
    const user = process.env.MYSQL_USER ?? '';
    const password = process.env.MYSQL_PASSWORD ?? '';
    const database = process.env.MYSQL_DATABASE ?? '';
    this.pool = createPool({
      host,
      port,
      user,
      password,
      database,
      waitForConnections: true,
      connectionLimit: 10,
    });
    this.db = drizzle(this.pool, { schema, mode: 'default' });
  }

  onModuleDestroy() {
    return this.pool.end();
  }
}
