import { DataType, ExcelVariable } from './api.models';

export function normalizeHeader(value: string): string {
  return value
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9_]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

export function matchHeaders(columns: ExcelVariable[], headers: string[]): Record<string, string> {
  const used = new Set<string>();
  return Object.fromEntries(
    columns.map((column) => {
      const source =
        headers.find(
          (header) =>
            !used.has(header) &&
            [
              normalizeHeader(column.headerName || column.code),
              normalizeHeader(column.code),
            ].includes(normalizeHeader(header)),
        ) || '';
      if (source) used.add(source);
      return [column.code, source];
    }),
  );
}

export function mapHeaderRows(
  rows: Record<string, unknown>[],
  columns: ExcelVariable[],
  mapping: Record<string, string>,
): Record<string, unknown>[] {
  return rows.map((row) =>
    Object.fromEntries(columns.map((column) => [column.code, row[mapping[column.code]] ?? ''])),
  );
}

export function validHeaderValue(type: DataType, value: unknown): boolean {
  const text = String(value ?? '').trim();
  if (!text) return true;
  switch (type) {
    case 'TEXT':
      return true;
    case 'NUMBER':
      return /^[+-]?(?:\d+(?:[.,]\d+)?|[.,]\d+)$/.test(text.replace(/[\s\u00a0\u202f]/g, ''));
    case 'PHONE':
      return text.replace(/\D/g, '').length >= 8;
    case 'EMAIL':
      return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(text);
    case 'BOOLEAN':
      return /^(true|false|oui|non|1|0)$/i.test(text);
    case 'DATE': {
      const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
      const french = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
      const year = Number(iso?.[1] ?? french?.[3]);
      const month = Number(iso?.[2] ?? french?.[2]);
      const day = Number(iso?.[3] ?? french?.[1]);
      const date = new Date(year, month - 1, day);
      return (
        !!(iso || french) &&
        date.getFullYear() === year &&
        date.getMonth() === month - 1 &&
        date.getDate() === day
      );
    }
  }
}

export function downloadWorkbook(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
