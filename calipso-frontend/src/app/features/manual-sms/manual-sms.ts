import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company, ManualSmsResponse } from '../../core/models/api.models';
import { CompanyService } from '../../core/services/company';
import { ManualSmsService } from '../../core/services/manual-sms';

@Component({
  selector: 'app-manual-sms',
  imports: [CommonModule, FormsModule],
  templateUrl: './manual-sms.html',
  styleUrl: './manual-sms.scss',
})
export class ManualSms implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly manualSmsService = inject(ManualSmsService);

  companies: Company[] = [];
  selectedCompanyId = 0;
  phoneNumbersText = '';
  message = '';
  error = '';
  result?: ManualSmsResponse;

  ngOnInit(): void {
    this.companyService.findAll().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.selectedCompanyId = companies[0]?.id || 0;
      },
      error: () => this.error = 'Impossible de charger les entreprises.',
    });
  }

  get phoneNumbers(): string[] {
    return this.phoneNumbersText
      .split(/[\n,;]+/)
      .map((value) => value.trim())
      .filter(Boolean);
  }

  get segmentEstimate(): number {
    if (!this.message.trim()) {
      return 0;
    }
    return this.message.length <= 160 ? 1 : Math.ceil(this.message.length / 153);
  }

  send(): void {
    this.error = '';
    this.result = undefined;

    const phoneNumbers = this.phoneNumbers;
    const selectedCompany = this.companies.find((company) => company.id === this.selectedCompanyId);
    const payload = {
      companyId: this.selectedCompanyId,
      message: this.message,
      phoneNumbers,
    };

    console.groupCollapsed('[Manual SMS] Valeurs envoyees');
    console.log('Payload API', payload);
    console.log('Entreprise', {
      id: selectedCompany?.id,
      name: selectedCompany?.name,
      senderPhone: selectedCompany?.senderPhone || '',
      smsBalance: selectedCompany?.smsBalance || 0,
    });
    console.log('Estimation', {
      caracteres: this.message.length,
      segmentsParDestinataire: this.segmentEstimate,
      unitesEstimees: phoneNumbers.length * this.segmentEstimate,
    });
    console.groupEnd();

    this.manualSmsService.send(payload).subscribe({
      next: (result) => {
        console.log('[Manual SMS] Reponse API', result);
        this.result = result;
      },
      error: (err) => {
        console.error('[Manual SMS] Erreur API', err);
        this.error = this.errorMessage(err);
      },
    });
  }

  private errorMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null && 'error' in err) {
      const body = (err as { error?: { detail?: string; message?: string; error?: string } }).error;
      return body?.detail || body?.message || body?.error || 'Envoi impossible.';
    }

    return 'Envoi impossible.';
  }
}
