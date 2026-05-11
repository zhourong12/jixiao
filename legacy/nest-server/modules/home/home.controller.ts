import { Controller, Get, Query, Req, UseGuards } from '@nestjs/common';
import { HomeService } from './home.service';
import type { Request } from 'express';
import { SessionUserGuard } from '../../guards/session-user.guard';

function parseYm(year?: string, month?: string): { year?: number; month?: number } {
  if (year == null || month == null) return {};
  const y = parseInt(year, 10);
  const m = parseInt(month, 10);
  if (!Number.isFinite(y) || !Number.isFinite(m)) return {};
  return { year: y, month: m };
}

@Controller('api/home')
@UseGuards(SessionUserGuard)
export class HomeController {
  constructor(private readonly homeService: HomeService) {}

  @Get('todos')
  async getTodos(
    @Req() req: Request,
    @Query('year') year?: string,
    @Query('month') month?: string,
  ) {
    return this.homeService.getTodos(req.userContext!.userId!, parseYm(year, month));
  }

  @Get('overview')
  async getOverview(
    @Req() req: Request,
    @Query('year') year?: string,
    @Query('month') month?: string,
  ) {
    return this.homeService.getOverview(req.userContext!.userId!, parseYm(year, month));
  }

  @Get('action-counts')
  async getActionCounts(
    @Req() req: Request,
    @Query('year') year?: string,
    @Query('month') month?: string,
  ) {
    return this.homeService.getActionCounts(req.userContext!.userId!, parseYm(year, month));
  }
}
