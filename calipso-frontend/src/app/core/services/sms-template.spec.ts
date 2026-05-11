import { TestBed } from '@angular/core/testing';

import { SmsTemplate } from './sms-template';

describe('SmsTemplate', () => {
  let service: SmsTemplate;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SmsTemplate);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
