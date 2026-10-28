import {
  ChangeDetectionStrategy,
  computed,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DataType,
  ExcelVariable,
  FileAnalysisResponse,
  ImportProfile,
  SmsTemplate,
} from '../../../core/models/api.models';
import { downloadWorkbook, normalizeHeader } from '../../../core/models/header-model';
import { ImportProfileService } from '../../../core/services/import-profile';
import { ExcelVariableService } from '../../../core/services/excel-variable';
import { CampaignService } from '../../../core/services/campaign';
import { SmsTemplateService } from '../../../core/services/sms-template';
import { WorkspaceService } from '../../../core/services/workspace';

function columnForm(column: Partial<ExcelVariable> = {}) {
  return new FormGroup({
    id: new FormControl<number | undefined>(column.id, { nonNullable: true }),
    included: new FormControl(true, { nonNullable: true }),
    headerName: new FormControl(column.headerName || column.code || '', {
      nonNullable: true,
      validators: Validators.required,
    }),
    code: new FormControl(column.code || '', {
      nonNullable: true,
      validators: Validators.required,
    }),
    label: new FormControl(column.label || column.headerName || column.code || '', {
      nonNullable: true,
    }),
    dataType: new FormControl<DataType>(column.dataType || 'TEXT', { nonNullable: true }),
    required: new FormControl(column.required || false, { nonNullable: true }),
    phone: new FormControl(column.phone || false, { nonNullable: true }),
  });
}

@Component({
  selector: 'app-import-profile-list',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './import-profile-list.html',
  styleUrl: './import-profile-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportProfileList {
  readonly workspace = inject(WorkspaceService);
  private readonly profilesApi = inject(ImportProfileService);
  private readonly variablesApi = inject(ExcelVariableService);
  private readonly campaignsApi = inject(CampaignService);
  private readonly templatesApi = inject(SmsTemplateService);
  private readonly destroyRef = inject(DestroyRef);
  readonly profiles = signal<ImportProfile[]>([]);
  readonly selectedId = signal<number | undefined>(undefined);
  readonly templates = signal<SmsTemplate[]>([]);
  readonly error = signal('');
  readonly success = signal('');
  readonly saving = signal(false);
  readonly loading = signal(false);
  readonly analyzing = signal(false);
  readonly archivePending = signal(false);
  readonly analysis = signal<FileAnalysisResponse | undefined>(undefined);
  readonly selectedFile = signal<File | undefined>(undefined);
  readonly dataTypes: DataType[] = ['TEXT', 'NUMBER', 'DATE', 'PHONE', 'EMAIL', 'BOOLEAN'];
  readonly typeLabels: Record<DataType, string> = {
    TEXT: 'Texte',
    NUMBER: 'Nombre',
    DATE: 'Date',
    PHONE: 'Téléphone',
    EMAIL: 'Email',
    BOOLEAN: 'Oui / Non',
  };
  readonly columns = new FormArray<ReturnType<typeof columnForm>>([]);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    description: new FormControl('', { nonNullable: true }),
    columns: this.columns,
  });
  readonly importOptions = new FormGroup({
    sheetNumber: new FormControl(1, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1)],
    }),
    headerRow: new FormControl(1, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1)],
    }),
  });
  readonly message = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    content: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  private readonly activeCompanyId = computed(() => this.workspace.activeCompany()?.id);

  constructor() {
    effect(() => {
      const companyId = this.activeCompanyId();
      untracked(() => {
        this.newModel();
        this.profiles.set([]);
        if (companyId) this.loadProfiles(companyId);
      });
    });
  }

  private loadProfiles(companyId: number): void {
    this.profilesApi
      .findByCompany(companyId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profiles) => {
          if (this.workspace.activeCompany()?.id === companyId) this.profiles.set(profiles);
        },
        error: () => this.error.set('Impossible de charger les modèles.'),
      });
  }

  newModel(): void {
    this.selectedId.set(undefined);
    this.templates.set([]);
    this.columns.clear();
    this.form.reset({ name: '', description: '' });
    this.message.reset();
    this.selectedFile.set(undefined);
    this.analysis.set(undefined);
    this.archivePending.set(false);
    this.error.set('');
    this.success.set('');
    this.loading.set(false);
  }

  selectModel(profile: ImportProfile): void {
    this.newModel();
    this.selectedId.set(profile.id);
    this.loading.set(true);
    this.form.patchValue({ name: profile.name, description: profile.description || '' });
    if (!profile.id) return;
    this.variablesApi
      .findByProfile(profile.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (columns) => {
          if (this.selectedId() !== profile.id) return;
          this.columns.clear();
          columns.forEach((column) => this.columns.push(columnForm(column)));
          this.form.markAsPristine();
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Impossible de charger les colonnes.');
        },
      });
    this.loadTemplates(profile.id);
  }

  addColumn(code = ''): void {
    this.columns.push(
      columnForm({
        code,
        headerName: code,
        label: code,
        dataType: code === 'TELEPHONE' ? 'PHONE' : 'TEXT',
      }),
    );
    this.form.markAsDirty();
  }

  removeColumn(index: number): void {
    this.columns.removeAt(index);
    this.form.markAsDirty();
  }

  moveColumn(index: number, direction: number): void {
    const control = this.columns.at(index);
    this.columns.removeAt(index);
    this.columns.insert(index + direction, control);
    this.form.markAsDirty();
  }

  selectPhone(index: number): void {
    this.columns.controls.forEach((column, i) => column.controls.phone.setValue(i === index));
    this.columns.at(index).patchValue({ dataType: 'PHONE', required: true, included: true });
    this.form.markAsDirty();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0]);
    this.analysis.set(undefined);
    this.importOptions.reset({ sheetNumber: 1, headerRow: 1 });
    input.value = '';
    if (this.selectedFile()) this.analyzeHeaders();
  }

  analyzeHeaders(): void {
    const file = this.selectedFile();
    if (!file || this.importOptions.invalid) return;
    this.analyzing.set(true);
    this.error.set('');
    const { sheetNumber, headerRow } = this.importOptions.getRawValue();
    this.campaignsApi
      .analyzeFile(file, sheetNumber - 1, headerRow)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.analyzing.set(false);
          if (file === this.selectedFile()) this.analysis.set(result);
        },
        error: (err) => {
          this.analyzing.set(false);
          this.analysis.set(undefined);
          this.error.set(err.error?.message || 'Impossible de lire les en-têtes.');
        },
      });
  }

  addImportedHeaders(): void {
    const result = this.analysis();
    if (!result) return;
    if (
      result.sheetIndex !== this.importOptions.controls.sheetNumber.value - 1 ||
      result.headerRow !== this.importOptions.controls.headerRow.value
    ) {
      this.error.set('Relisez les en-têtes pour appliquer la feuille et la ligne choisies.');
      return;
    }
    const used = new Set(
      this.columns.getRawValue().map((column) => normalizeHeader(column.headerName)),
    );
    for (const code of result.headers) {
      if (used.has(code)) continue;
      const headerName = result.headerLabels?.[code] || code;
      const dataType: DataType = /TELEPHONE|PHONE|MOBILE|PORTABLE|^TEL$/.test(code)
        ? 'PHONE'
        : /EMAIL|MAIL/.test(code)
          ? 'EMAIL'
          : 'TEXT';
      this.columns.push(
        columnForm({
          code: /^[A-Z]/.test(code) ? code : `COL_${code}`,
          headerName,
          label: headerName,
          dataType,
        }),
      );
      used.add(code);
    }
    this.form.markAsDirty();
    this.success.set(
      'En-têtes ajoutés. Vérifiez les types et désignez le destinataire SMS avant d’enregistrer.',
    );
    this.analysis.set(undefined);
  }

  saveModel(): void {
    const companyId = this.workspace.activeCompany()?.id;
    if (!companyId || this.saving() || this.loading() || this.analyzing()) return;
    const raw = this.form.getRawValue();
    const columns: ExcelVariable[] = raw.columns
      .filter((c) => c.included)
      .map((c) => ({
        id: c.id,
        code: normalizeHeader(c.code),
        headerName: c.headerName.trim(),
        label: c.label.trim() || c.headerName.trim(),
        dataType: c.phone ? 'PHONE' : c.dataType,
        required: c.required || c.phone,
        phone: c.phone,
      }));
    if (
      !raw.name.trim() ||
      !columns.length ||
      columns.some((c) => !c.headerName || !/^[A-Z][A-Z0-9_]*$/.test(c.code))
    ) {
      this.error.set(
        'Saisissez un nom de modèle et au moins une colonne avec un en-tête et un code valide.',
      );
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.success.set('');
    this.profilesApi
      .saveModel(
        { companyId, name: raw.name.trim(), description: raw.description, columns },
        this.selectedId(),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.saving.set(false);
          if (companyId !== this.workspace.activeCompany()?.id) return;
          this.loadProfiles(companyId);
          this.selectModel(profile);
          this.success.set('Modèle d’en-têtes enregistré.');
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err.error?.message || 'Impossible d’enregistrer le modèle.');
        },
      });
  }

  duplicate(): void {
    const id = this.selectedId();
    if (!id) return;
    this.saving.set(true);
    this.profilesApi
      .duplicate(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.saving.set(false);
          this.loadProfiles(this.workspace.activeCompany()?.id || 0);
          this.selectModel(profile);
          this.success.set('Structure du modèle dupliquée.');
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err.error?.message || 'Impossible de dupliquer le modèle.');
        },
      });
  }

  archive(): void {
    const id = this.selectedId();
    const companyId = this.workspace.activeCompany()?.id;
    if (!id || !companyId) return;
    this.saving.set(true);
    this.profilesApi
      .archive(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.newModel();
          this.loadProfiles(companyId);
          this.success.set('Modèle archivé. Les campagnes existantes sont conservées.');
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err.error?.message || 'Impossible d’archiver le modèle.');
        },
      });
  }

  download(): void {
    const id = this.selectedId();
    if (!id) return;
    this.profilesApi
      .download(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => downloadWorkbook(blob, 'modele-entetes.xlsx'),
        error: () => this.error.set('Impossible de télécharger le modèle enregistré.'),
      });
  }

  private loadTemplates(id: number): void {
    this.templatesApi
      .findByProfile(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (templates) => {
          if (this.selectedId() === id) this.templates.set(templates);
        },
        error: () => this.error.set('Impossible de charger les messages du modèle.'),
      });
  }

  insertVariable(code: string): void {
    this.message.controls.content.setValue(
      `${this.message.controls.content.value} {{${normalizeHeader(code)}}}`.trim(),
    );
  }

  saveMessage(): void {
    const id = this.selectedId();
    if (!id || this.message.invalid || this.form.dirty) {
      this.error.set('Enregistrez la structure puis renseignez le nom et le contenu du message.');
      return;
    }
    const message = this.message.getRawValue();
    const codes = this.columns.getRawValue().map((c) => c.code);
    const unknown = [...message.content.matchAll(/\{\{\s*([\w-]+)\s*\}\}/g)]
      .map((m) => m[1])
      .filter((code) => !codes.includes(code));
    if (unknown.length) {
      this.error.set(`Variables inconnues : ${unknown.join(', ')}`);
      return;
    }
    this.saving.set(true);
    this.templatesApi
      .create({ profileId: id, ...message })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.message.reset();
          this.loadTemplates(id);
          this.success.set('Message enregistré pour ce modèle.');
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err.error?.message || 'Impossible d’enregistrer le message.');
        },
      });
  }
}
