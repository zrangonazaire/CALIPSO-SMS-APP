import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { Campaign, CampaignChannel, Recipient } from '../../../core/models/api.models';
import { matchesSearch, paginate } from '../../../core/models/pagination';
import { CampaignService } from '../../../core/services/campaign';
import { RecipientService } from '../../../core/services/recipient';
import { WorkspaceService } from '../../../core/services/workspace';
import { Pagination } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-campaign-list',
  imports: [CommonModule, FormsModule, RouterLink, Pagination],
  templateUrl: './campaign-list.html',
  styleUrl: './campaign-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampaignList {
  readonly workspace = inject(WorkspaceService);
  private readonly campaignService = inject(CampaignService);
  private readonly recipientService = inject(RecipientService);
  private readonly destroyRef = inject(DestroyRef);
  private campaignRequest?: Subscription;
  private recipientRequest?: Subscription;
  private readonly campaignList = viewChild<ElementRef<HTMLElement>>('campaignList');
  private readonly recipientTitle = viewChild<ElementRef<HTMLElement>>('recipientTitle');
  readonly campaigns = signal<Campaign[]>([]);
  readonly recipients = signal<Recipient[]>([]);
  readonly selectedCampaign = signal<Campaign | undefined>(undefined);
  readonly channelFilter = signal<'ALL' | CampaignChannel>('ALL');
  readonly campaignStatusFilter = signal('ALL');
  readonly searchQuery = signal('');
  readonly recipientSearch = signal('');
  readonly recipientStatusFilter = signal('ALL');
  readonly campaignPage = signal(1);
  readonly recipientPage = signal(1);
  readonly error = signal('');
  readonly recipientError = signal('');
  readonly success = signal('');
  readonly campaignsLoading = signal(false);
  readonly previewLoading = signal(false);
  readonly sending = signal(false);

  readonly filteredCampaigns = computed(() => this.campaigns().filter(c =>
    (this.channelFilter() === 'ALL' || (c.channel || 'SMS') === this.channelFilter())
    && (this.campaignStatusFilter() === 'ALL' || c.status === this.campaignStatusFilter())
    && matchesSearch([c.name, c.description], this.searchQuery()),
  ));
  readonly campaignStatuses = computed(() => [...new Set(this.campaigns().map(c => c.status).filter((s): s is string => !!s))]);
  readonly pagedCampaigns = computed(() => paginate(this.filteredCampaigns(), this.campaignPage(), 10));
  readonly filteredRecipients = computed(() => this.recipients().filter(r =>
    (this.recipientStatusFilter() === 'ALL' || r.status === this.recipientStatusFilter())
    && matchesSearch([r.recipientName, r.phoneNumber, r.email, r.generatedMessage, ...Object.values(r.rawData || {})], this.recipientSearch()),
  ));
  readonly recipientStatuses = computed(() => [...new Set(this.recipients().map(r => r.status).filter((s): s is string => !!s))]);
  readonly pagedRecipients = computed(() => paginate(this.filteredRecipients(), this.recipientPage(), 25));
  readonly selectedCampaignRecipients = computed(() => this.selectedCampaign()?.totalRecipients ?? this.recipients().length);
  readonly validRecipients = computed(() => this.recipients().filter(r => ['VALID', 'SENT', 'DELIVERED'].includes(r.status || '')).length);
  readonly invalidRecipients = computed(() => this.recipients().filter(r => r.status === 'INVALID').length);
  readonly totalSegments = computed(() => this.selectedCampaign()?.totalSegments ?? this.recipients().reduce((sum, r) => sum + (r.segmentCount || 0), 0));
  readonly previewColumns = computed(() => [...new Set(this.recipients().flatMap(r => Object.keys(r.rawData || {})))].slice(0, 5));

  constructor() {
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      untracked(() => {
        this.campaignRequest?.unsubscribe(); this.recipientRequest?.unsubscribe();
        this.campaigns.set([]); this.recipients.set([]); this.selectedCampaign.set(undefined);
        this.campaignPage.set(1); this.recipientPage.set(1);
        this.error.set(''); this.recipientError.set(''); this.success.set('');
        this.previewLoading.set(false); this.campaignsLoading.set(false); this.sending.set(false);
        if (companyId) this.loadCampaigns(companyId);
      });
    });
    effect(() => {
      this.channelFilter(); this.campaignStatusFilter(); this.searchQuery();
      this.campaignPage.set(1);
    });
    effect(() => {
      this.recipientSearch(); this.recipientStatusFilter();
      this.recipientPage.set(1);
    });
    effect(() => {
      const visible = this.pagedCampaigns().items;
      const list = this.campaignList();
      if (list) list.nativeElement.scrollTop = 0;
      untracked(() => {
        const selected = this.selectedCampaign()?.id;
        if (!visible.some(c => c.id === selected)) {
          if (visible[0]) this.selectCampaign(visible[0]);
          else { this.recipientRequest?.unsubscribe(); this.selectedCampaign.set(undefined); this.recipients.set([]); this.previewLoading.set(false); }
        }
      });
    });
  }

  changeRecipientPage(page: number): void {
    this.recipientPage.set(page);
    const title = this.recipientTitle()?.nativeElement;
    title?.scrollIntoView?.({ block: 'start' });
    title?.focus({ preventScroll: true });
  }

  loadCampaigns(companyId = this.workspace.selectedCompanyId()): void {
    if (!companyId) return;
    this.campaignRequest?.unsubscribe(); this.campaignsLoading.set(true);
    this.campaignRequest = this.campaignService.findByCompany(companyId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: campaigns => {
        if (companyId !== this.workspace.selectedCompanyId()) return;
        this.campaigns.set(campaigns); this.campaignsLoading.set(false);
        const updated = campaigns.find(c => c.id === this.selectedCampaign()?.id);
        if (updated) { this.selectedCampaign.set(updated); this.loadRecipients(updated); }
      },
      error: () => { this.campaignsLoading.set(false); this.error.set('Impossible de charger vos campagnes.'); },
    });
  }

  selectCampaign(campaign: Campaign): void {
    if (campaign.id !== this.selectedCampaign()?.id) {
      this.recipientSearch.set(''); this.recipientStatusFilter.set('ALL'); this.recipientPage.set(1);
    }
    this.selectedCampaign.set(campaign); this.success.set(''); this.error.set('');
    this.loadRecipients(campaign);
  }

  loadRecipients(campaign: Campaign): void {
    this.recipientRequest?.unsubscribe(); this.recipients.set([]); this.recipientError.set('');
    if (!campaign.id) { this.previewLoading.set(false); return; }
    this.previewLoading.set(true);
    this.recipientRequest = this.recipientService.findByCampaign(campaign.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: recipients => {
        this.recipients.set(recipients); this.previewLoading.set(false);
      },
      error: () => { this.previewLoading.set(false); this.recipientError.set('Impossible de charger les destinataires de cette campagne.'); },
    });
  }

  importExcel(campaign: Campaign, event: Event): void {
    const input = event.target as HTMLInputElement, file = input.files?.[0];
    if (!campaign.id || !file) return;
    const companyId = this.workspace.selectedCompanyId();
    this.error.set(''); this.success.set('');
    this.campaignService.importExcel(campaign.id, file).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: updated => {
        if (companyId !== this.workspace.selectedCompanyId()) return;
        this.success.set(`Fichier importé : ${updated.totalValid || 0} contact(s) prêts.`);
        this.loadCampaigns(companyId);
      },
      error: err => { if (companyId === this.workspace.selectedCompanyId()) this.error.set(err.error?.message || 'Import du fichier impossible.'); },
    });
    input.value = '';
  }

  sendCampaign(campaign: Campaign): void {
    if (!campaign.id || this.sending()) return;
    const companyId = this.workspace.selectedCompanyId();
    this.sending.set(true); this.error.set(''); this.success.set('');
    this.campaignService.send(campaign.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: updated => {
        if (companyId !== this.workspace.selectedCompanyId()) return;
        this.sending.set(false);
        this.success.set(`Campagne ${updated.name} : ${updated.totalSent || 0} message(s) accepté(s) par le fournisseur, ${updated.totalFailed || 0} échec(s). La réception dépend des accusés de livraison.`);
        this.workspace.refreshCompanies(companyId); this.loadCampaigns(companyId);
      },
      error: err => {
        if (companyId !== this.workspace.selectedCompanyId()) return;
        this.sending.set(false); this.error.set(err.error?.message || 'Envoi impossible.');
      },
    });
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon',
      IMPORTED: 'Prête à envoyer',
      SENDING: 'Envoi en cours',
      COMPLETED: 'Traitement terminé',
      FAILED: 'Échec',
      VALID: 'Prêt',
      INVALID: 'Invalide',
      SENT: 'Accepté par le fournisseur',
      DELIVERED: 'Livré',
      PENDING: 'En attente',
    };
    return status ? labels[status] || status : '-';
  }

  statusClass(status?: string): string {
    if (status === 'SENT') {
      return 'badge-soft-primary';
    }
    if (status === 'COMPLETED' || status === 'VALID' || status === 'DELIVERED') {
      return 'badge-soft-success';
    }
    if (status === 'INVALID' || status === 'FAILED') {
      return 'badge-soft-danger';
    }
    if (status === 'IMPORTED') {
      return 'badge-soft-primary';
    }
    return 'badge-soft-warning';
  }
}
