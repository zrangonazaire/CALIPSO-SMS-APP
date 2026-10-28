import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ImportProfileList } from './import-profile-list';
import { WorkspaceService } from '../../../core/services/workspace';
import { API_BASE } from '../../../core/api';

describe('ImportProfileList', () => {
  let http: HttpTestingController;
  const company = signal({ id: 1, name: 'École' });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ImportProfileList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: WorkspaceService, useValue: { activeCompany: company } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('imports a header-only file and saves its structure without importing recipients', () => {
    const fixture = TestBed.createComponent(ImportProfileList);
    fixture.detectChanges();
    http.expectOne(`${API_BASE}/import-profiles/company/1`).flush([]);
    const component = fixture.componentInstance;
    component.form.controls.name.setValue('Relance');
    const file = new File(['Téléphone;Prénom'], 'modele.csv', { type: 'text/csv' });
    const input = { files: [file], value: 'modele.csv' };
    component.onFileSelected({ target: input } as unknown as Event);
    const analysis = http.expectOne(`${API_BASE}/campaigns/analyze-file`);
    expect(analysis.request.body.get('headerRow')).toBe('1');
    analysis.flush({
      fileName: 'modele.csv',
      headers: ['TELEPHONE', 'PRENOM'],
      headerLabels: { TELEPHONE: 'Téléphone', PRENOM: 'Prénom' },
      totalRows: 0,
      allRows: [],
      sampleRows: [],
      sheetNames: ['CSV'],
      sheetIndex: 0,
      headerRow: 1,
    });
    component.addImportedHeaders();
    component.selectPhone(0);
    component.saveModel();
    const save = http.expectOne(`${API_BASE}/import-profiles/header-models`);
    expect(save.request.body.columns).toHaveLength(2);
    expect(save.request.body.columns[0]).toMatchObject({
      headerName: 'Téléphone',
      code: 'TELEPHONE',
      required: true,
      phone: true,
      dataType: 'PHONE',
    });
    expect(save.request.body.rows).toBeUndefined();
    save.flush({ id: 7, name: 'Relance', company: { id: 1 } });
    http.expectOne(`${API_BASE}/import-profiles/company/1`).flush([{ id: 7, name: 'Relance' }]);
    http
      .expectOne(`${API_BASE}/excel-variables/profile/7`)
      .flush([
        {
          id: 8,
          code: 'TELEPHONE',
          headerName: 'Téléphone',
          label: 'Téléphone',
          dataType: 'PHONE',
          required: true,
          phone: true,
        },
      ]);
    http.expectOne(`${API_BASE}/sms-templates/profile/7`).flush([]);
    fixture.detectChanges();
    expect(component.selectedId()).toBe(7);
    expect(component.form.pristine).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Télécharger Excel vide');
  });

  it('creates columns individually and only keeps selected columns', () => {
    const fixture = TestBed.createComponent(ImportProfileList);
    fixture.detectChanges();
    http.expectOne(`${API_BASE}/import-profiles/company/1`).flush([]);
    const component = fixture.componentInstance;
    component.form.controls.name.setValue('Clients');
    component.addColumn('TELEPHONE');
    component.addColumn('PRENOM');
    component.selectPhone(0);
    component.columns.at(1).controls.included.setValue(false);
    component.saveModel();
    const save = http.expectOne(`${API_BASE}/import-profiles/header-models`);
    expect(save.request.body.columns).toHaveLength(1);
    save.flush({ message: 'Échec simulé' }, { status: 400, statusText: 'Bad Request' });
    expect(component.error()).toBe('Échec simulé');
    expect(component.columns.length).toBe(2);
  });
});
