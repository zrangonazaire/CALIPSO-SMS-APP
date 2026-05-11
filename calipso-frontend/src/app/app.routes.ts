import { Routes } from '@angular/router';
import { MainLayout } from './layout/main-layout/main-layout';
import { Dashboard } from './features/dashboard/dashboard';
import { CompanyList } from './features/companies/company-list/company-list';
import { ImportProfileList } from './features/import-profiles/import-profile-list/import-profile-list';
import { ExcelVariableList } from './features/excel-variables/excel-variable-list/excel-variable-list';
import { SmsTemplateList } from './features/sms-templates/sms-template-list/sms-template-list';
import { CampaignList } from './features/campaigns/campaign-list/campaign-list';
import { CampaignCreate } from './features/campaigns/campaign-create/campaign-create';
import { RecipientList } from './features/recipient-list/recipient-list';
import { Subscription } from './features/subscription/subscription';
import { ManualSms } from './features/manual-sms/manual-sms';
import { Login } from './features/login/login';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: '',
    component: MainLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: Dashboard },
      { path: 'companies', component: CompanyList },
      { path: 'import-profiles', component: ImportProfileList },
      { path: 'excel-variables', component: ExcelVariableList },
      { path: 'sms-templates', component: SmsTemplateList },
      { path: 'manual-sms', component: ManualSms },
      { path: 'campaigns', component: CampaignList },
      { path: 'campaigns/create', component: CampaignCreate },
      { path: 'recipients', component: RecipientList },
      { path: 'subscription', component: Subscription }
    ]
  }
];
