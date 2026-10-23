import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { WorkspaceService } from '../../core/services/workspace';

@Component({
  selector: 'app-header',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  readonly auth = inject(AuthService);
  readonly workspace = inject(WorkspaceService);

  onCompanySwitch(companyId: number): void {
    this.workspace.selectCompany(companyId);
  }

  logout(): void {
    this.auth.logout();
  }
}
