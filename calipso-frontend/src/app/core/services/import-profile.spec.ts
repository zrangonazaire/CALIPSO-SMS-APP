import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ImportProfileService } from './import-profile';

describe('ImportProfileService', () => {
  let service: ImportProfileService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ImportProfileService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
