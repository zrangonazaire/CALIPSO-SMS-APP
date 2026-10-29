import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { merge } from 'rxjs';
import { DatePeriod, PeriodPreset, isCalendarDate, presetPeriod } from '../../core/models/date-period';

@Component({
  selector: 'app-period-filter',
  imports: [ReactiveFormsModule],
  templateUrl: './period-filter.html',
  styleUrl: './period-filter.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PeriodFilter {
  readonly idPrefix = input.required<string>();
  readonly basis = input('');
  readonly periodChange = output<DatePeriod>();
  private readonly destroyRef = inject(DestroyRef);
  readonly error = signal('');
  readonly pending = signal(false);
  private readonly applied = signal<DatePeriod>({});
  readonly summary = computed(() => {
    const period = this.applied();
    const display = (date: string) => date.split('-').reverse().join('/');
    return period.startDate && period.endDate ? `Du ${display(period.startDate)} au ${display(period.endDate)} inclus` : 'Toute la période';
  });
  readonly form = new FormGroup({
    preset: new FormControl<PeriodPreset>('ALL', { nonNullable: true }),
    startDate: new FormControl('', { nonNullable: true }),
    endDate: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.form.controls.preset.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(preset => {
      this.error.set('');
      if (preset === 'CUSTOM') { this.pending.set(true); return; }
      const period = presetPeriod(preset);
      this.form.patchValue({ startDate: period.startDate || '', endDate: period.endDate || '' }, { emitEvent: false });
      this.apply(period);
    });
    merge(this.form.controls.startDate.valueChanges, this.form.controls.endDate.valueChanges)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        this.form.controls.preset.setValue('CUSTOM', { emitEvent: false });
        this.pending.set(true); this.error.set('');
      });
  }

  applyCustom(): void {
    const { preset, startDate, endDate } = this.form.getRawValue();
    if (preset === 'ALL') { this.apply({}); return; }
    if (!isCalendarDate(startDate) || !isCalendarDate(endDate)) {
      this.error.set('Renseignez une date de début et une date de fin valides.'); return;
    }
    if (startDate > endDate) {
      this.error.set('La date de début doit précéder ou être égale à la date de fin.'); return;
    }
    this.apply({ startDate, endDate });
  }

  reset(): void {
    this.form.setValue({ preset: 'ALL', startDate: '', endDate: '' }, { emitEvent: false });
    this.apply({});
  }

  private apply(period: DatePeriod): void {
    this.applied.set(period); this.pending.set(false); this.error.set(''); this.periodChange.emit(period);
  }
}
