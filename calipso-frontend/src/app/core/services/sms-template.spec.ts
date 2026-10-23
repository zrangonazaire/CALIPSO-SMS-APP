import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { SmsTemplateService } from './sms-template';

describe('SmsTemplateService', () => {
  let service: SmsTemplateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SmsTemplateService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
