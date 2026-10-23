import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CampaignChannel, FileAnalysisResponse } from '../../../core/models/api.models';
import { CampaignService } from '../../../core/services/campaign';
import { WorkspaceService } from '../../../core/services/workspace';

interface StarterTemplate {
  id: string;
  title: string;
  category: string;
  channels: CampaignChannel[];
  subject?: string;
  content: string;
  ctaLabel?: string;
  ctaUrl?: string;
}

@Component({
  selector: 'app-campaign-create',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './campaign-create.html',
  styleUrl: './campaign-create.scss',
})
export class CampaignCreate implements OnInit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly workspace = inject(WorkspaceService);
  readonly campaignService = inject(CampaignService);

  step: 1 | 2 | 3 | 4 = 1;
  audienceSource: 'FILE' | 'MANUAL' = 'FILE';

  name = '';
  description = '';
  channel: CampaignChannel = 'SMS';
  emailSubject = '';
  messageTemplate = '';
  recipientColumn = '';
  mediaUrl = '';
  ctaLabel = '';
  ctaUrl = '';

  analyzingFile = false;
  submitting = false;
  error = '';
  analysis?: FileAnalysisResponse;

  manualContactsText = '';
  previewRowIndex = 0;

  readonly quickCampaignNames = [
    'Promotion Spéciale Clients',
    'Relance Échéance & Paiement',
    'Rappel Rendez-vous',
    'Actualités & Nouveautés',
  ];

  readonly starterTemplates: StarterTemplate[] = [
    {
      id: 'promo',
      title: 'Offre Promotionnelle / Vente Flash',
      category: 'Commerce',
      channels: ['SMS', 'WHATSAPP', 'EMAIL'],
      subject: '🔥 Offre exclusive pour vous, {{PRENOM}} !',
      content:
        'Bonjour {{PRENOM}}, profitez de -20% sur toute notre nouvelle collection jusqu\'à dimanche ! Présentez ce message en boutique ou commandez en ligne.',
      ctaLabel: 'Profiter de l\'offre',
      ctaUrl: 'https://votre-boutique.ci/promo',
    },
    {
      id: 'relance',
      title: 'Rappel de Facture / Scolarité',
      category: 'Finance & Écoles',
      channels: ['SMS', 'WHATSAPP', 'EMAIL'],
      subject: 'Rappel d\'échéance - Dossier de {{PRENOM}} {{NOM}}',
      content:
        'Bonjour {{PRENOM}} {{NOM}}, nous vous rappelons que votre solde de {{MONTANT}} arrive à échéance. Merci de procéder au règlement ou de contacter notre service comptable.',
      ctaLabel: 'Régler maintenant',
      ctaUrl: 'https://paiement.exemple.ci',
    },
    {
      id: 'event',
      title: 'Invitation Événement / Réunion',
      category: 'Événementiel',
      channels: ['SMS', 'WHATSAPP', 'EMAIL'],
      subject: 'Invitation officielle : Nous vous attendons {{PRENOM}} !',
      content:
        'Cher(e) {{PRENOM}}, nous avons le plaisir de vous convier à notre rencontre annuelle à {{VILLE}}. Confirmez vite votre présence !',
      ctaLabel: 'Confirmer ma présence',
      ctaUrl: 'https://invitation.exemple.ci',
    },
    {
      id: 'livraison',
      title: 'Suivi de Commande / Livraison',
      category: 'Logistique',
      channels: ['SMS', 'WHATSAPP'],
      content:
        'Bonjour {{PRENOM}}, votre commande est prête et sera livrée aujourd\'hui à {{VILLE}}. Notre livreur vous contactera sur le {{TELEPHONE}}.',
    },
  ];

  readonly currentCompanyBalance = computed(() => {
    return this.workspace.activeCompany()?.smsBalance ?? 0;
  });

  ngOnInit(): void {
    const ch = this.route.snapshot.queryParamMap.get('channel') as CampaignChannel | null;
    if (ch && ['SMS', 'WHATSAPP', 'EMAIL'].includes(ch)) {
      this.selectChannel(ch);
    }
    const tplName = this.route.snapshot.queryParamMap.get('templateName');
    const tplContent = this.route.snapshot.queryParamMap.get('templateContent');
    if (tplContent) {
      this.messageTemplate = tplContent;
      if (tplName && !this.name) {
        this.name = tplName;
      }
    }
  }

  selectChannel(ch: CampaignChannel): void {
    this.channel = ch;
    if (this.analysis) {
      if (ch === 'EMAIL' && this.analysis.detectedEmailColumn) {
        this.recipientColumn = this.analysis.detectedEmailColumn;
      } else if (ch !== 'EMAIL' && this.analysis.detectedPhoneColumn) {
        this.recipientColumn = this.analysis.detectedPhoneColumn;
      }
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadAndAnalyze(file);
    input.value = '';
  }

  uploadAndAnalyze(file: File): void {
    this.analyzingFile = true;
    this.error = '';

    this.campaignService.analyzeFile(file).subscribe({
      next: (res) => {
        this.analyzingFile = false;
        this.analysis = res;
        this.previewRowIndex = 0;

        if (this.channel === 'EMAIL') {
          this.recipientColumn = res.detectedEmailColumn || res.headers[0] || '';
        } else {
          this.recipientColumn = res.detectedPhoneColumn || res.headers[0] || '';
        }

        if (!this.name.trim()) {
          const cleanName = file.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');
          this.name = 'Campagne ' + cleanName.charAt(0).toUpperCase() + cleanName.slice(1);
        }
      },
      error: (err) => {
        this.analyzingFile = false;
        this.error = err.error?.message || 'Impossible de lire ce fichier. Vérifiez qu\'il s\'agit d\'un fichier Excel (.xlsx, .xls) ou CSV.';
      },
    });
  }

  loadDemoContacts(): void {
    this.audienceSource = 'MANUAL';
    if (this.channel === 'EMAIL') {
      this.manualContactsText =
        'awa.kouassi@exemple.ci; Awa; Kouassi; 25 000 FCFA; Abidjan\n' +
        'marc.konan@exemple.ci; Marc; Konan; 18 500 FCFA; Bouaké\n' +
        'fatou.diallo@exemple.ci; Fatou; Diallo; 42 000 FCFA; Yamoussoukro';
    } else {
      this.manualContactsText =
        '+2250708091011; Awa; Kouassi; 25 000 FCFA; Abidjan\n' +
        '+2250504030201; Marc; Konan; 18 500 FCFA; Bouaké\n' +
        '+2250102030405; Fatou; Diallo; 42 000 FCFA; Yamoussoukro';
    }
    this.parseManualContacts();
  }

  parseManualContacts(): void {
    const lines = this.manualContactsText
      .split(/\r?\n/)
      .map((l) => l.trim())
      .filter((l) => l.length > 0);

    const targetCol = this.channel === 'EMAIL' ? 'EMAIL' : 'TELEPHONE';
    const headers = [targetCol, 'PRENOM', 'NOM', 'MONTANT', 'VILLE'];

    const rows: Record<string, unknown>[] = lines.map((line) => {
      const parts = line.includes(';') ? line.split(';') : line.split(',');
      return {
        [targetCol]: (parts[0] || '').trim(),
        PRENOM: (parts[1] || 'Client').trim(),
        NOM: (parts[2] || '').trim(),
        MONTANT: (parts[3] || '').trim(),
        VILLE: (parts[4] || 'Abidjan').trim(),
      };
    });

    this.recipientColumn = targetCol;
    this.analysis = {
      fileName: 'Saisie directe (' + rows.length + ' contacts)',
      headers,
      detectedPhoneColumn: 'TELEPHONE',
      detectedEmailColumn: 'EMAIL',
      detectedNameColumn: 'PRENOM',
      totalRows: rows.length,
      sampleRows: rows.slice(0, 10),
      allRows: rows,
    };
    this.previewRowIndex = 0;
  }

  get availableColumns(): string[] {
    if (this.analysis && this.analysis.headers.length > 0) {
      return this.analysis.headers;
    }
    return ['TELEPHONE', 'PRENOM', 'NOM', 'EMAIL', 'MONTANT', 'VILLE'];
  }

  get activeRows(): Record<string, unknown>[] {
    return this.analysis?.allRows || [];
  }

  get currentPreviewRow(): Record<string, unknown> {
    const rows = this.activeRows;
    if (rows.length > 0) {
      const idx = Math.min(Math.max(0, this.previewRowIndex), rows.length - 1);
      return rows[idx];
    }
    return {
      TELEPHONE: '+2250708091011',
      PRENOM: 'Awa',
      NOM: 'Kouassi',
      EMAIL: 'awa.kouassi@exemple.ci',
      MONTANT: '25 000 FCFA',
      VILLE: 'Abidjan',
    };
  }

  prevPreviewRow(): void {
    if (this.previewRowIndex > 0) {
      this.previewRowIndex--;
    }
  }

  nextPreviewRow(): void {
    if (this.previewRowIndex < this.activeRows.length - 1) {
      this.previewRowIndex++;
    }
  }

  insertVariable(code: string): void {
    const token = `{{${code}}}`;
    this.messageTemplate = (this.messageTemplate || '') + (this.messageTemplate.endsWith(' ') || !this.messageTemplate ? '' : ' ') + token;
  }

  applyStarterTemplate(tpl: StarterTemplate): void {
    this.messageTemplate = tpl.content;
    if (tpl.subject && !this.emailSubject) {
      this.emailSubject = tpl.subject;
    }
    if (tpl.ctaLabel) {
      this.ctaLabel = tpl.ctaLabel;
    }
    if (tpl.ctaUrl) {
      this.ctaUrl = tpl.ctaUrl;
    }
  }

  renderInterpolated(template: string, row: Record<string, unknown>): string {
    if (!template) {
      return '';
    }
    return template.replace(/\{\{\s*([a-zA-Z0-9_-]+)\s*\}\}/g, (_, key: string) => {
      const foundKey = Object.keys(row).find((k) => k.toLowerCase() === key.toLowerCase());
      const val = foundKey ? row[foundKey] : undefined;
      return val !== undefined && val !== null && String(val).trim() !== '' ? String(val) : `{{${key}}}`;
    });
  }

  get livePreviewMessage(): string {
    const rendered = this.renderInterpolated(this.messageTemplate, this.currentPreviewRow);
    return rendered || 'Rédigez votre message ou choisissez un modèle prêt à l\'emploi pour voir l\'aperçu en direct sur le téléphone...';
  }

  get livePreviewSubject(): string {
    const rendered = this.renderInterpolated(this.emailSubject, this.currentPreviewRow);
    return rendered || this.name || 'Objet de votre email';
  }

  get livePreviewRecipient(): string {
    const row = this.currentPreviewRow;
    const target = this.recipientColumn && row[this.recipientColumn] ? String(row[this.recipientColumn]) : '';
    const prenom = row['PRENOM'] || row['NOM'] || '';
    if (prenom && target) {
      return `${prenom} (${target})`;
    }
    return target || String(prenom) || 'Destinataire exemple';
  }

  get unitsPerMessage(): number {
    if (!this.messageTemplate.trim()) {
      return 0;
    }
    if (this.channel === 'EMAIL' || this.channel === 'WHATSAPP') {
      return 1;
    }
    const len = this.livePreviewMessage.length;
    const isUnicode = /[^\u0000-\u007f]/.test(this.livePreviewMessage);
    const single = isUnicode ? 70 : 160;
    const multi = isUnicode ? 67 : 153;
    return len <= single ? 1 : Math.ceil(len / multi);
  }

  get totalEstimatedCredits(): number {
    return this.activeRows.length * this.unitsPerMessage;
  }

  canGoNext(): boolean {
    if (this.step === 1) {
      return this.name.trim().length > 0;
    }
    if (this.step === 2) {
      if (this.audienceSource === 'MANUAL' && (!this.analysis || this.analysis.totalRows === 0)) {
        this.parseManualContacts();
      }
      return this.activeRows.length > 0 && this.recipientColumn.trim().length > 0;
    }
    if (this.step === 3) {
      return this.messageTemplate.trim().length > 0;
    }
    return true;
  }

  nextStep(): void {
    this.error = '';
    if (this.step === 2 && this.audienceSource === 'MANUAL') {
      this.parseManualContacts();
    }
    if (!this.canGoNext()) {
      this.error = 'Veuillez compléter les informations de cette étape avant de continuer.';
      return;
    }
    if (this.step < 4) {
      this.step = (this.step + 1) as 1 | 2 | 3 | 4;
    }
  }

  prevStep(): void {
    this.error = '';
    if (this.step > 1) {
      this.step = (this.step - 1) as 1 | 2 | 3 | 4;
    }
  }

  submitWizard(sendImmediately: boolean): void {
    const companyId = this.workspace.activeCompany()?.id;
    if (!companyId) {
      this.error = 'Aucun espace entreprise actif détecté.';
      return;
    }

    if (this.audienceSource === 'MANUAL') {
      this.parseManualContacts();
    }

    this.submitting = true;
    this.error = '';

    this.campaignService
      .createFromWizard({
        companyId,
        name: this.name.trim(),
        description: this.description.trim(),
        channel: this.channel,
        messageTemplate: this.messageTemplate,
        emailSubject: this.emailSubject,
        recipientColumn: this.recipientColumn,
        mediaUrl: this.mediaUrl,
        ctaLabel: this.ctaLabel,
        ctaUrl: this.ctaUrl,
        rows: this.activeRows,
        sendImmediately,
      })
      .subscribe({
        next: () => {
          this.submitting = false;
          this.workspace.refreshCompanies(companyId);
          this.router.navigateByUrl('/campaigns');
        },
        error: (err) => {
          this.submitting = false;
          this.error = err.error?.message || 'Une erreur est survenue lors du traitement de la campagne.';
        },
      });
  }
}
