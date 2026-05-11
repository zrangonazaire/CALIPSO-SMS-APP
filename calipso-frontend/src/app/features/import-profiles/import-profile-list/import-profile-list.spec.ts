import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportProfileList } from './import-profile-list';

describe('ImportProfileList', () => {
  let component: ImportProfileList;
  let fixture: ComponentFixture<ImportProfileList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImportProfileList],
    }).compileComponents();

    fixture = TestBed.createComponent(ImportProfileList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
