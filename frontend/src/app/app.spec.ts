import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.setItem('lookalike.disclosure.accepted', 'true');
    await TestBed.configureTestingModule({
      imports: [App]
    }).compileComponents();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('renders the application shell', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('mat-toolbar')?.textContent).toContain('Lookalike');
    expect(compiled.querySelector('h1')?.textContent).toContain('Fedelobo');
  });
});
