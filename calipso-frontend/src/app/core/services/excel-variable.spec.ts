import { TestBed } from '@angular/core/testing';

import { ExcelVariable } from './excel-variable';

describe('ExcelVariable', () => {
  let service: ExcelVariable;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ExcelVariable);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
