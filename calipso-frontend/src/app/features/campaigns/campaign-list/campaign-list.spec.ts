import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { CampaignList } from './campaign-list';

describe('CampaignList', () => {
  let component: CampaignList;
  let fixture: ComponentFixture<CampaignList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(CampaignList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
