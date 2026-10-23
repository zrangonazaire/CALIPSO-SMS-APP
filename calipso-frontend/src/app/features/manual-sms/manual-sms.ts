import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CampaignChannel, ManualSmsResponse } from '../../core/models/api.models';
import { ManualSmsService } from '../../core/services/manual-sms';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-manual-sms',
  imports: [CommonModule, FormsModule],
  templateUrl: './manual-sms.html',
  styleUrl: './manual-sms.scss',
})
export class ManualSms {
  readonly workspace = inject(WorkspaceService);
  private readonly manualSmsService = inject(ManualSmsService);

  channel: CampaignChannel = 'SMS';
  subject = '';
  phoneNumbersText = '';
  message = '';
  ctaLabel = '';
  ctaUrl = '';
  error = '';
  sending = false;
  result?: ManualSmsResponse;

  readonly quickMessages = [
    {
      label: 'Confirmation RDV',
      subject: 'Confirmation de votre rendez-vous',
      text: 'Bonjour, nous vous confirmons votre rendez-vous demain à 10h00 dans nos locaux. À très bientôt !',
    },
    {
      label: 'Relance rapide',
      subject: 'Rappel de règlement',
      text: 'Bonjour, sauf erreur de notre part, votre facture reste en attente de règlement. Merci de votre diligence.',
    },
    {
      label: 'Code / Alerte',
      subject: 'Notification importante',
      text: 'Bonjour, votre dossier est prêt et disponible à l\'accueil. Vous pouvez passer le récupérer dès aujourd\'hui.',
    },
  ];

  get phoneNumbers(): string[] {
    return this.phoneNumbersText
      .split(/[\n,;]+/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  get segmentEstimate(): number {
    if (!this.message.trim()) {
      return 0;
    }
    if (this.channel === 'WHATSAPP' || this.channel === 'EMAIL') {
      return 1;
    }
    const unicode = [...this.message].some((char) => char.charCodeAt(0) > 127);
    const singleLimit = unicode ? 70 : 160;
    const multiLimit = unicode ? 67 : 153;
    return this.message.length <= singleLimit ? 1 : Math.ceil(this.message.length / multiLimit);
  }

  send(): void {
    const companyId = this.workspace.selectedCompanyId();
    if (!companyId) {
      this.error = 'Aucun espace actif détecté.';
      return;
    }

    this.error = '';
    this.result = undefined;
    this.sending = true;

    this.manualSmsService
      .send({
        companyId,
        channel: this.channel,
        subject: this.subject,
        message: this.message,
        phoneNumbers: this.phoneNumbers,
        ctaLabel: this.ctaLabel,
        ctaUrl: this.ctaUrl,
      })
      .subscribe({
        next: (result) => {
          this.sending = false;
          this.result = result;
          this.workspace.refreshCompanies(companyId);
        },
        error: (err) => {
          this.sending = false;
          this.error = err.error?.message || 'Envoi direct impossible.';
        },
      });
  }
}
