import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PeriodFilter } from '../../shared/period-filter/period-filter';
import { DatePeriod } from '../../core/models/date-period';
import { CampaignChannel, SmsSendHistory } from '../../core/models/api.models';
import { SmsHistoryService } from '../../core/services/sms-history';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-sms-history',
  imports: [CommonModule, FormsModule, PeriodFilter],
  templateUrl: './sms-history.html',
  styleUrl: './sms-history.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SmsHistory {
  readonly workspace = inject(WorkspaceService);
  private readonly historyService = inject(SmsHistoryService);

  readonly history = signal<SmsSendHistory[]>([]);
  readonly channelFilter = signal<'ALL' | CampaignChannel>('ALL');
  readonly sourceFilter = signal<'ALL' | 'CAMPAIGN' | 'MANUAL'>('ALL');
  readonly statusFilter = signal('ALL');
  readonly search = signal('');
  readonly error = signal('');
  readonly loading = signal(false);
  readonly period = signal<DatePeriod>({});

  constructor() {
    effect(onCleanup => {
      const companyId = this.workspace.selectedCompanyId();
      const period = this.period();
      this.history.set([]); this.error.set(''); this.loading.set(false);
      if (!companyId) return;
      this.loading.set(true);
      const subscription = this.historyService.findByCompany(companyId, period).subscribe({
        next: history => { this.history.set(history); this.loading.set(false); },
        error: () => { this.loading.set(false); this.error.set('Impossible de charger le journal des envois.'); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  readonly filteredHistory = computed(() => {
    const term = this.search().trim().toLowerCase();

    return this.history().filter((item) => {
      const ch = item.channel || 'SMS';
      const matchesChannel = this.channelFilter() === 'ALL' || ch === this.channelFilter();
      const matchesSource = this.sourceFilter() === 'ALL' || item.source === this.sourceFilter();
      const matchesStatus = this.statusFilter() === 'ALL' || item.status === this.statusFilter();
      const matchesSearch =
        !term ||
        item.phoneNumber.toLowerCase().includes(term) ||
        item.message.toLowerCase().includes(term) ||
        (item.campaignName || '').toLowerCase().includes(term);

      return matchesChannel && matchesSource && matchesStatus && matchesSearch;
    });
  });

  get statuses(): string[] {
    return [...new Set(this.history().map((item) => item.status))];
  }

  get totalSegments(): number {
    return this.filteredHistory().reduce((total, item) => total + (item.segmentCount || 0), 0);
  }

  get campaignCount(): number {
    return this.filteredHistory().filter((item) => item.source === 'CAMPAIGN').length;
  }

  get manualCount(): number {
    return this.filteredHistory().filter((item) => item.source === 'MANUAL').length;
  }

  sourceLabel(source: string): string {
    return source === 'CAMPAIGN' ? 'Campagne' : 'Envoi Express';
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      SENT: 'Envoyé',
      DELIVERED: 'Livré',
      FAILED: 'Échec',
      VALID: 'Valide',
      INVALID: 'Invalide',
      PENDING: 'En attente',
    };
    return labels[status] || status;
  }

  statusClass(status: string): string {
    if (status === 'DELIVERED' || status === 'SENT') {
      return 'badge-soft-success';
    }
    if (status === 'FAILED' || status === 'INVALID') {
      return 'badge-soft-danger';
    }
    return 'badge-soft-warning';
  }
}
