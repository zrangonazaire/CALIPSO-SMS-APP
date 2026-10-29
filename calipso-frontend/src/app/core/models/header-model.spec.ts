import { ExcelVariable } from './api.models';
import { mapHeaderRows, matchHeaders, normalizeHeader, validHeaderValue } from './header-model';

describe('Header models', () => {
  const columns: ExcelVariable[] = [
    {
      code: 'TELEPHONE',
      headerName: 'Téléphone client',
      label: 'Téléphone',
      dataType: 'PHONE',
      required: true,
      phone: true,
    },
    {
      code: 'MONTANT',
      headerName: 'Montant dû',
      label: 'Montant',
      dataType: 'NUMBER',
      required: false,
      phone: false,
    },
  ];

  it('matches accents and reordered columns without reusing a source', () => {
    expect(normalizeHeader('  Téléphone-client ')).toBe('TELEPHONE_CLIENT');
    expect(matchHeaders(columns, ['MONTANT_DU', 'TELEPHONE_CLIENT'])).toEqual({
      TELEPHONE: 'TELEPHONE_CLIENT',
      MONTANT: 'MONTANT_DU',
    });
    expect(
      matchHeaders([...columns, { ...columns[0], code: 'AUTRE' }], ['TELEPHONE_CLIENT'])['AUTRE'],
    ).toBe('');
  });

  it('maps renamed columns while retaining zero and false without mutating the source', () => {
    const rows = [{ MOBILE: '0701234567', SOLDE: 0, NOTE: false }];
    expect(mapHeaderRows(rows, columns, { TELEPHONE: 'MOBILE', MONTANT: 'SOLDE' })).toEqual([
      { TELEPHONE: '0701234567', MONTANT: 0 },
    ]);
    expect(rows[0]).toEqual({ MOBILE: '0701234567', SOLDE: 0, NOTE: false });
  });

  it('checks numbers, real calendar dates, email and phone fields', () => {
    expect(validHeaderValue('NUMBER', '25 000,50')).toBe(true);
    expect(validHeaderValue('NUMBER', 'abc')).toBe(false);
    expect(validHeaderValue('DATE', '29/02/2024')).toBe(true);
    expect(validHeaderValue('DATE', '31/02/2026')).toBe(false);
    expect(validHeaderValue('DATE', '2026-10-28')).toBe(true);
    expect(validHeaderValue('PHONE', '0701234567')).toBe(true);
    expect(validHeaderValue('EMAIL', 'invalide')).toBe(false);
    expect(validHeaderValue('BOOLEAN', false)).toBe(true);
  });
});
