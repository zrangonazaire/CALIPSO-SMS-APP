import { TestBed } from '@angular/core/testing';
import { DatePeriod } from '../../core/models/date-period';
import { PeriodFilter } from './period-filter';

describe('PeriodFilter', () => {
  it('applies presets immediately and rejects invalid custom ranges before emitting', () => {
    const fixture = TestBed.createComponent(PeriodFilter);
    fixture.componentRef.setInput('idPrefix', 'test-period');
    const component = fixture.componentInstance;
    const changes: DatePeriod[] = [];
    component.periodChange.subscribe(period => changes.push(period));
    fixture.detectChanges();
    component.form.controls.preset.setValue('LAST_7_DAYS');
    expect(changes).toHaveLength(1);
    component.form.controls.startDate.setValue('2026-10-29');
    component.form.controls.endDate.setValue('2026-10-01');
    component.applyCustom();
    expect(changes).toHaveLength(1); expect(component.error()).toContain('date de début');
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    component.form.controls.startDate.setValue('2026-10-01');
    component.form.controls.endDate.setValue('2026-10-28');
    component.applyCustom();
    expect(changes.at(-1)).toEqual({ startDate: '2026-10-01', endDate: '2026-10-28' });
    expect(component.summary()).toContain('28/10/2026 inclus');
    component.reset(); expect(changes.at(-1)).toEqual({}); expect(component.summary()).toBe('Toute la période');
  });
  it('requires both custom dates and preserves the last valid selection', () => {
    const fixture = TestBed.createComponent(PeriodFilter);
    fixture.componentRef.setInput('idPrefix', 'test-period');
    const component = fixture.componentInstance;
    const changes: DatePeriod[] = [];
    component.periodChange.subscribe(period => changes.push(period));
    component.form.controls.preset.setValue('CUSTOM'); component.applyCustom();
    expect(changes).toEqual([]); expect(component.error()).toContain('valides');
  });
});
