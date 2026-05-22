import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, SmsSendHistory } from '../../core/models/api.models';
import { CompanyService } from '../../core/services/company';
import { SmsHistoryService } from '../../core/services/sms-history';

@Component({
  selector: 'app-sms-history',
  imports: [CommonModule, FormsModule],
  templateUrl: './sms-history.html',
  styleUrl: './sms-history.scss',
})
export class SmsHistory implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly historyService = inject(SmsHistoryService);

  companies: Company[] = [];
  history: SmsSendHistory[] = [];
  selectedCompanyId = 0;
  sourceFilter: 'ALL' | 'CAMPAIGN' | 'MANUAL' = 'ALL';
  statusFilter = 'ALL';
  search = '';
  error = '';
  loading = false;

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
        if (this.selectedCompanyId) {
          this.loadHistory();
        }
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  loadHistory(): void {
    this.error = '';
    this.loading = true;
    this.historyService.findByCompany(this.selectedCompanyId).subscribe({
      next: (history) => {
        this.history = history;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.error = 'Impossible de charger l historique des SMS.';
      },
    });
  }

  get filteredHistory(): SmsSendHistory[] {
    const term = this.search.trim().toLowerCase();

    return this.history.filter((item) => {
      const matchesSource = this.sourceFilter === 'ALL' || item.source === this.sourceFilter;
      const matchesStatus = this.statusFilter === 'ALL' || item.status === this.statusFilter;
      const matchesSearch = !term
        || item.phoneNumber.toLowerCase().includes(term)
        || item.message.toLowerCase().includes(term)
        || (item.campaignName || '').toLowerCase().includes(term);

      return matchesSource && matchesStatus && matchesSearch;
    });
  }

  get statuses(): string[] {
    return [...new Set(this.history.map((item) => item.status))];
  }

  get totalSegments(): number {
    return this.filteredHistory.reduce((total, item) => total + (item.segmentCount || 0), 0);
  }

  get campaignCount(): number {
    return this.filteredHistory.filter((item) => item.source === 'CAMPAIGN').length;
  }

  get manualCount(): number {
    return this.filteredHistory.filter((item) => item.source === 'MANUAL').length;
  }

  sourceLabel(source: string): string {
    return source === 'CAMPAIGN' ? 'Campagne' : 'Envoi cible';
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      SENT: 'Accepte Orange',
      DELIVERED: 'Livre',
      FAILED: 'Echec',
      VALID: 'Valide',
      INVALID: 'Invalide',
      PENDING: 'En attente',
    };

    return labels[status] || status;
  }

  statusClass(status: string): string {
    if (status === 'DELIVERED') {
      return 'badge-soft-success';
    }

    if (status === 'FAILED' || status === 'INVALID') {
      return 'badge-soft-danger';
    }

    return 'badge-soft-warning';
  }
}
