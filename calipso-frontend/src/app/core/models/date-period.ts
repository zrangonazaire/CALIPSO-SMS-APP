import { HttpParams } from '@angular/common/http';

export interface DatePeriod { startDate?: string; endDate?: string; }
export type PeriodPreset = 'ALL' | 'TODAY' | 'YESTERDAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'THIS_MONTH' | 'LAST_MONTH' | 'CUSTOM';

export function periodParams(period: DatePeriod = {}): HttpParams {
  let params = new HttpParams();
  if (period.startDate) params = params.set('startDate', period.startDate);
  if (period.endDate) params = params.set('endDate', period.endDate);
  return params;
}

export function presetPeriod(preset: Exclude<PeriodPreset, 'CUSTOM'>, now = new Date()): DatePeriod {
  if (preset === 'ALL') return {};
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Abidjan', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find(item => item.type === type)?.value);
  const year = part('year'), month = part('month') - 1, day = part('day');
  const iso = (offset: number, monthIndex = month) => new Date(Date.UTC(year, monthIndex, offset)).toISOString().slice(0, 10);
  const today = iso(day);
  switch (preset) {
    case 'TODAY': return { startDate: today, endDate: today };
    case 'YESTERDAY': return { startDate: iso(day - 1), endDate: iso(day - 1) };
    case 'LAST_7_DAYS': return { startDate: iso(day - 6), endDate: today };
    case 'LAST_30_DAYS': return { startDate: iso(day - 29), endDate: today };
    case 'THIS_MONTH': return { startDate: iso(1), endDate: today };
    case 'LAST_MONTH': return { startDate: iso(1, month - 1), endDate: iso(0) };
  }
}

export function isCalendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
