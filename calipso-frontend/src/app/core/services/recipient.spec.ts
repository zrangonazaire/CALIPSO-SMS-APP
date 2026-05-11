import { TestBed } from '@angular/core/testing';

import { Recipient } from './recipient';

describe('Recipient', () => {
  let service: Recipient;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Recipient);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
