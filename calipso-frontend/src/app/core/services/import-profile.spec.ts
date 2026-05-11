import { TestBed } from '@angular/core/testing';

import { ImportProfile } from './import-profile';

describe('ImportProfile', () => {
  let service: ImportProfile;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ImportProfile);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
