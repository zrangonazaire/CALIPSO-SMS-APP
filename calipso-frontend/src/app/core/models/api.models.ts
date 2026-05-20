export type DataType = 'TEXT' | 'NUMBER' | 'DATE' | 'PHONE' | 'EMAIL' | 'BOOLEAN';

export interface Company {
  id?: number;
  name: string;
  email?: string;
  phone?: string;
  senderPhone?: string;
  address?: string;
  contactName?: string;
  businessType?: string;
  smsBalance?: number;
  active?: boolean;
  createdAt?: string;
}

export type CompanyUserRole = 'ADMIN' | 'MANAGER' | 'OPERATOR' | 'VIEWER';

export interface CompanyUser {
  id?: number;
  companyId?: number;
  company?: Company;
  fullName: string;
  username: string;
  email: string;
  password?: string;
  phone?: string;
  role: CompanyUserRole;
  active?: boolean;
  createdAt?: string;
}

export interface ImportProfile {
  id?: number;
  companyId?: number;
  company?: Company;
  name: string;
  description?: string;
  active?: boolean;
  createdAt?: string;
}

export interface ExcelVariable {
  id?: number;
  profileId?: number;
  profile?: ImportProfile;
  code: string;
  label: string;
  dataType: DataType;
  required: boolean;
  phone: boolean;
  active?: boolean;
  createdAt?: string;
}

export interface SmsTemplate {
  id?: number;
  profileId?: number;
  profile?: ImportProfile;
  name: string;
  content: string;
  active?: boolean;
  createdAt?: string;
}

export interface Campaign {
  id?: number;
  profileId?: number;
  templateId?: number;
  phoneVariableId?: number;
  company?: Company;
  profile?: ImportProfile;
  template?: SmsTemplate;
  phoneVariable?: ExcelVariable;
  name: string;
  description?: string;
  status?: string;
  totalRecipients?: number;
  totalValid?: number;
  totalInvalid?: number;
  totalSegments?: number;
  totalSent?: number;
  totalFailed?: number;
  createdAt?: string;
}

export interface Recipient {
  id?: number;
  campaign?: Campaign;
  phoneNumber?: string;
  rawData?: Record<string, unknown>;
  generatedMessage?: string;
  segmentCount?: number;
  status?: string;
  errorMessage?: string;
  sentAt?: string;
  createdAt?: string;
}

export interface ManualSmsRequest {
  companyId: number;
  message: string;
  phoneNumbers: string[];
}

export interface ManualSmsResponse {
  requestedRecipients: number;
  acceptedRecipients: number;
  segmentsPerRecipient: number;
  totalSegments: number;
  remainingSmsBalance: number;
}

export type SmsSendSource = 'CAMPAIGN' | 'MANUAL';
export type SubscriptionPlanCode = 'ESSENTIEL' | 'STANDARD' | 'AVANCE' | 'VOLUME';
export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
export type WalletTransactionType = 'CREDIT' | 'DEBIT' | 'REFUND';

export interface SmsSendHistory {
  id: number;
  companyId: number;
  companyName: string;
  campaignId?: number;
  campaignName?: string;
  source: SmsSendSource;
  phoneNumber: string;
  message: string;
  segmentCount: number;
  status: string;
  errorMessage?: string;
  sentAt: string;
}

export interface SubscriptionPlan {
  id: number;
  code: SubscriptionPlanCode;
  name: string;
  pricePerSms: number;
  durationDays: number;
  minRechargeUnits: number;
  active: boolean;
}

export interface CompanySubscription {
  id: number;
  planCode: SubscriptionPlanCode;
  planName: string;
  pricePerSms: number;
  durationDays: number;
  status: SubscriptionStatus;
  startedAt: string;
  expiresAt: string;
}

export interface CompanySubscriptionSummary {
  companyId: number;
  companyName: string;
  smsBalance: number;
  activeSubscription?: CompanySubscription | null;
}

export interface WalletTransaction {
  id: number;
  companyId: number;
  companyName: string;
  subscriptionId?: number;
  type: WalletTransactionType;
  smsUnits: number;
  unitPrice?: number;
  amount?: number;
  reason: string;
  createdAt: string;
}
