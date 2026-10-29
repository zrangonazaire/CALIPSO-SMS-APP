import { isCalendarDate, periodParams, presetPeriod } from './date-period';

describe('Statistics periods', () => {
  it('counts rolling periods inclusively and handles month boundaries', () => {
    const now = new Date('2026-01-03T12:00:00Z');
    expect(presetPeriod('LAST_7_DAYS', now)).toEqual({ startDate: '2025-12-28', endDate: '2026-01-03' });
    expect(presetPeriod('LAST_30_DAYS', now)).toEqual({ startDate: '2025-12-05', endDate: '2026-01-03' });
    expect(presetPeriod('LAST_MONTH', now)).toEqual({ startDate: '2025-12-01', endDate: '2025-12-31' });
    expect(presetPeriod('LAST_MONTH', new Date('2024-03-10T12:00:00Z'))).toEqual({ startDate: '2024-02-01', endDate: '2024-02-29' });
  });
  it('uses the Abidjan date rather than the host or supplied offset', () => {
    const now = new Date('2026-10-28T23:30:00-05:00');
    expect(presetPeriod('TODAY', now)).toEqual({ startDate: '2026-10-29', endDate: '2026-10-29' });
    expect(presetPeriod('YESTERDAY', now)).toEqual({ startDate: '2026-10-28', endDate: '2026-10-28' });
    expect(presetPeriod('THIS_MONTH', now)).toEqual({ startDate: '2026-10-01', endDate: '2026-10-29' });
  });
  it('validates calendar dates and omits empty URL parameters', () => {
    expect(isCalendarDate('2026-02-31')).toBe(false);
    expect(isCalendarDate('2024-02-29')).toBe(true);
    expect(isCalendarDate('invalid')).toBe(false);
    expect(periodParams({}).keys()).toEqual([]);
    expect(periodParams({ startDate: '2026-10-01' }).get('startDate')).toBe('2026-10-01');
    expect(presetPeriod('ALL')).toEqual({});
  });
});
