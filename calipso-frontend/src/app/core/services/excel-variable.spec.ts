import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ExcelVariableService } from './excel-variable';

describe('ExcelVariableService', () => {
  let service: ExcelVariableService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ExcelVariableService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
