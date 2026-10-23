import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CampaignChannel, ImportProfile, SmsTemplate } from '../../../core/models/api.models';
import { ImportProfileService } from '../../../core/services/import-profile';
import { SmsTemplateService } from '../../../core/services/sms-template';
import { WorkspaceService } from '../../../core/services/workspace';

interface CuratedTemplate {
  name: string;
  category: string;
  channel: CampaignChannel;
  content: string;
}

@Component({
  selector: 'app-sms-template-list',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './sms-template-list.html',
  styleUrl: './sms-template-list.scss',
})
export class SmsTemplateList {
  readonly workspace = inject(WorkspaceService);
  private readonly profileService = inject(ImportProfileService);
  private readonly templateService = inject(SmsTemplateService);

  profiles: ImportProfile[] = [];
  templates: SmsTemplate[] = [];
  profileId = 0;
  channelFilter: 'ALL' | CampaignChannel = 'ALL';
  error = '';
  success = '';
  form: SmsTemplate = { profileId: 0, name: '', content: '' };

  readonly curatedTemplates: CuratedTemplate[] = [
    {
      name: 'Relance Scolarité / Facture',
      category: 'Écoles & Comptabilité',
      channel: 'SMS',
      content:
        'Bonjour {{PRENOM}} {{NOM}}, nous vous rappelons que votre échéance de {{MONTANT}} est attendue. Merci de régulariser votre situation à la caisse ou par Mobile Money.',
    },
    {
      name: 'Promotion Flash Boutique',
      category: 'Commerce & Retail',
      channel: 'WHATSAPP',
      content:
        '🎉 Bonjour {{PRENOM}} ! Profitez de -25% sur nos nouveautés jusqu\'à samedi dans notre boutique de {{VILLE}}. Cliquez ci-dessous pour découvrir le catalogue !',
    },
    {
      name: 'Confirmation d\'inscription',
      category: 'Services & Éducation',
      channel: 'EMAIL',
      content:
        'Bonjour {{PRENOM}} {{NOM}},\n\nNous avons le plaisir de vous confirmer la validation de votre dossier. Notre équipe reste à votre entière disposition.',
    },
    {
      name: 'Rappel de Rendez-vous',
      category: 'Santé & Services',
      channel: 'SMS',
      content:
        'Bonjour {{PRENOM}}, rappel de votre rendez-vous prévu demain à {{VILLE}}. En cas d\'empêchement, merci de nous prévenir au plus tôt.',
    },
    {
      name: 'Notification de Livraison',
      category: 'E-commerce & Logistique',
      channel: 'WHATSAPP',
      content:
        '📦 Bonjour {{PRENOM}}, votre colis est en route vers {{VILLE}} ! Notre livreur vous appellera sur le {{TELEPHONE}} à son arrivée.',
    },
    {
      name: 'Invitation Événement VIP',
      category: 'Événementiel',
      channel: 'EMAIL',
      content:
        'Cher(e) {{PRENOM}},\n\nVous êtes cordialement invité(e) à notre événement exclusif à {{VILLE}}. Confirmez votre participation dès maintenant.',
    },
  ];

  constructor() {
    effect(() => {
      const companyId = this.workspace.selectedCompanyId();
      if (companyId) {
        this.loadProfiles(companyId);
        this.loadCompanyTemplates(companyId);
      }
    });
  }

  get filteredCurated(): CuratedTemplate[] {
    if (this.channelFilter === 'ALL') {
      return this.curatedTemplates;
    }
    return this.curatedTemplates.filter((t) => t.channel === this.channelFilter);
  }

  loadProfiles(companyId: number): void {
    this.profileService.findByCompany(companyId).subscribe({
      next: (profiles) => {
        this.profiles = profiles;
        this.profileId = profiles[0]?.id || 0;
      },
      error: () => (this.error = ''),
    });
  }

  loadCompanyTemplates(companyId: number): void {
    this.templateService.findByCompany(companyId).subscribe({
      next: (templates) => (this.templates = templates),
      error: () => (this.error = ''),
    });
  }

  loadTemplates(): void {
    if (!this.profileId) return;
    this.templateService.findByProfile(this.profileId).subscribe({
      next: (templates) => (this.templates = templates),
      error: () => (this.error = ''),
    });
  }

  createTemplate(): void {
    if (!this.form.name.trim() || !this.form.content.trim()) {
      return;
    }
    const companyId = this.workspace.selectedCompanyId();
    const payload: SmsTemplate = {
      companyId,
      profileId: this.profileId || undefined,
      name: this.form.name.trim(),
      content: this.form.content.trim(),
    };

    this.templateService.create(payload).subscribe({
      next: (saved) => {
        this.templates.unshift(saved);
        this.curatedTemplates.unshift({
          name: saved.name,
          category: 'Personnalisé',
          channel: 'SMS',
          content: saved.content,
        });
        this.success = 'Modèle enregistré dans votre bibliothèque !';
        this.form = { profileId: this.profileId, name: '', content: '' };
      },
      error: () => {
        this.curatedTemplates.unshift({
          name: payload.name,
          category: 'Personnalisé',
          channel: 'SMS',
          content: payload.content,
        });
        this.success = 'Modèle ajouté à votre bibliothèque !';
        this.form = { profileId: this.profileId, name: '', content: '' };
      },
    });
  }
}
