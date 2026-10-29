import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { CampaignCreate } from './campaign-create';
import { WorkspaceService } from '../../../core/services/workspace';
import { API_BASE } from '../../../core/api';

describe('CampaignCreate header models', () => {
  let http: HttpTestingController;
  const company = signal({ id: 1, name: 'École', smsBalance: 100 });
  const phone = {
    id: 8,
    code: 'TELEPHONE',
    headerName: 'Téléphone client',
    label: 'Téléphone',
    dataType: 'PHONE',
    required: true,
    phone: true,
  };
  const amount = {
    id: 9,
    code: 'MONTANT',
    headerName: 'Montant dû',
    label: 'Montant',
    dataType: 'NUMBER',
    required: true,
    phone: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CampaignCreate],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: WorkspaceService,
          useValue: { activeCompany: company, refreshCompanies: vi.fn() },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => http.verify());

  function setup() {
    const fixture = TestBed.createComponent(CampaignCreate);
    fixture.detectChanges();
    http.expectOne(`${API_BASE}/import-profiles/company/1`).flush([{ id: 7, name: 'Relance' }]);
    const component = fixture.componentInstance;
    component.profileControl.setValue(7);
    http.expectOne(`${API_BASE}/excel-variables/profile/7`).flush([phone, amount]);
    http
      .expectOne(`${API_BASE}/sms-templates/profile/7`)
      .flush([{ id: 10, name: 'Rappel', content: 'Solde {{MONTANT}}' }]);
    return { fixture, component };
  }

  it('previews canonical variables and sends the original rows with their explicit mapping', () => {
    const { fixture, component } = setup();
    const file = new File([''], 'clients.csv');
    component.selectedFile = file;
    component.uploadAndAnalyze(file);
    http.expectOne(`${API_BASE}/campaigns/analyze-file`).flush({
      fileName: 'clients.csv',
      headers: ['PORTABLE', 'SOLDE'],
      totalRows: 1,
      allRows: [{ PORTABLE: '0701234567', SOLDE: 0 }],
      sampleRows: [{ PORTABLE: '0701234567', SOLDE: 0 }],
      sheetIndex: 0,
      headerRow: 1,
    });
    component.columnMapping.setValue({ TELEPHONE: 'PORTABLE', MONTANT: 'SOLDE' });
    component.name = 'Relance';
    component.messageTemplate = 'Solde {{MONTANT}}';
    component.step = 3;
    expect(component.livePreviewMessage).toBe('Solde 0');
    expect(component.canGoNext()).toBe(true);
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Messages associés au modèle');
    component.submitWizard(false);
    const create = http.expectOne(`${API_BASE}/campaigns/wizard`);
    expect(create.request.body.profileId).toBe(7);
    expect(create.request.body.columnMapping).toEqual({ TELEPHONE: 'PORTABLE', MONTANT: 'SOLDE' });
    expect(create.request.body.rows).toEqual([{ PORTABLE: '0701234567', SOLDE: 0 }]);
    create.flush({ id: 20 });
  });

  it('blocks missing mappings and unknown message variables', () => {
    const { component } = setup();
    component.name = 'Relance';
    component.messageTemplate = 'Bonjour {{INCONNU}}';
    component.submitWizard(false);
    http.expectNone(`${API_BASE}/campaigns/wizard`);
    expect(component.mappingIssues.some((issue) => issue.includes('MONTANT'))).toBe(true);
    expect(component.unknownMessageVariables).toContain('INCONNU');
    expect(component.submitting).toBe(false);
  });
});
