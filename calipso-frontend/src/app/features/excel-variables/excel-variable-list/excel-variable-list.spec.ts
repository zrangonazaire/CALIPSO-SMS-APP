import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExcelVariableList } from './excel-variable-list';

describe('ExcelVariableList', () => {
  let component: ExcelVariableList;
  let fixture: ComponentFixture<ExcelVariableList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExcelVariableList],
    }).compileComponents();

    fixture = TestBed.createComponent(ExcelVariableList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
