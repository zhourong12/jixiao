import { NestFactory } from '@nestjs/core';
import { Logger } from '@nestjs/common';
import { configureApp } from '@lark-apaas/fullstack-nestjs-core';
import { join } from 'path';
import { __express as hbsExpressEngine } from 'hbs';
import type { NextFunction, Request, Response } from 'express';

import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { attachViteProxyUpgrade, getViteProxy, isViteDevPath } from './utils/vite-dev-proxy';

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    abortOnError: process.env.NODE_ENV !== 'development',
  });
  await configureApp(app, { 
    disableSwagger: true,
  });
  if (process.env.NODE_ENV === 'development') {
    const expressApp = app.getHttpAdapter().getInstance();
    expressApp.use((req: Request, res: Response, next: NextFunction) => {
      const path = (req.originalUrl ?? '').split('?')[0] ?? '';
      if (!isViteDevPath(path)) {
        return next();
      }
      getViteProxy().web(req, res, {}, (err) => {
        if (err) next(err);
      });
    });
  }
  const logger = new Logger('Bootstrap');
  const host =
    process.env.SERVER_HOST ||
    (process.env.NODE_ENV === 'development' ? '0.0.0.0' : 'localhost');
  const port = Number(process.env.SERVER_PORT || '3000');

  // 注册视图引擎, 渲染 client 目录下的 html 文件
  app.setBaseViewsDir(join(process.cwd(), 'dist/client'));
  app.setViewEngine('html');
  app.engine('html', hbsExpressEngine);

  await app.listen(port, host);
  if (process.env.NODE_ENV === 'development') {
    attachViteProxyUpgrade(app.getHttpServer());
  }
  logger.log(`Server running on ${host}:${port}`);
  logger.log(`API endpoints ready at http://${host}:${port}/api`);
}

bootstrap();
