import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddTrackFormComponent } from './add-track-form.component';

describe('AddTrackFormComponent', () => {
  let component: AddTrackFormComponent;
  let fixture: ComponentFixture<AddTrackFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddTrackFormComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddTrackFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
