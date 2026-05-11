import { Controller, Get, Render, Req } from '@nestjs/common';
import type { Request } from 'express';
import { readSessionFromRequest } from '../../utils/session-token';

@Controller()
export class ViewController {

  @Get(['/', '*'])
  @Render('index')
  async render(@Req() req: Request): Promise<{ __platform__: string; _userInfo: string }>  {
    // you can add custom render params here
    const platformData = req.__platform_data__ ?? {};
    const session = readSessionFromRequest(req.cookies);
    const userInfo = session
      ? {
          user_id: session.sub,
          name: session.name,
          email: '',
          avatar: '',
        }
      : {};
    return {
      // don't delete this line, it's used by client to get platform info
      __platform__: JSON.stringify(platformData),
      _userInfo: JSON.stringify(userInfo),
    };
  }
}
