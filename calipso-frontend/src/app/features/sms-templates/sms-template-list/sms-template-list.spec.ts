import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SmsTemplateList } from './sms-template-list';

describe('SmsTemplateList', () => {
  let component: SmsTemplateList;
  let fixture: ComponentFixture<SmsTemplateList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SmsTemplateList],
    }).compileComponents();

    fixture = TestBed.createComponent(SmsTemplateList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
