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

    this.manualSmsService.send({
      companyId: this.selectedCompanyId,
      message: this.message,
      phoneNumbers: this.phoneNumbers,
    }).subscribe({
      next: (result) => this.result = result,
      error: (err) => this.error = err.error?.message || 'Envoi impossible.',
    });
  }
}
