import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecipientList } from './recipient-list';

describe('RecipientList', () => {
  let component: RecipientList;
  let fixture: ComponentFixture<RecipientList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecipientList],
    }).compileComponents();

    fixture = TestBed.createComponent(RecipientList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
