import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth';
import { Login } from './login';

describe('Registration availability', () => {
  function createLogin() {
    const configuration = new Subject<{ publicRegistrationEnabled: boolean }>();
    const register = vi.fn();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), {
        provide: AuthService,
        useValue: { configuration: () => configuration, register },
      }],
    });
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, configuration, register };
  }

  it('blocks registration before configuration and when disabled by the server', () => {
    const { fixture, component, configuration, register } = createLogin();
    component.switchMode('register');
    expect(component.mode).toBe('login');
    configuration.next({ publicRegistrationEnabled: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Créer un compte gratuit');
    component.registerAccount();
    expect(register).not.toHaveBeenCalled();
  });

  it('keeps registration closed if the configuration cannot be loaded', () => {
    const { component, configuration } = createLogin();
    configuration.error(new Error('Network unavailable'));
    component.switchMode('register');
    expect(component.mode).toBe('login');
  });

  it('allows registration when explicitly enabled', () => {
    const { component, configuration } = createLogin();
    configuration.next({ publicRegistrationEnabled: true });
    component.switchMode('register');
    expect(component.mode).toBe('register');
  });
});
