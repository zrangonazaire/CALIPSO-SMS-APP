export type DataType = 'TEXT' | 'NUMBER' | 'DATE' | 'PHONE' | 'EMAIL' | 'BOOLEAN';
export type CampaignChannel = 'SMS' | 'WHATSAPP' | 'EMAIL';

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
  headerName?: string;
  columnOrder?: number;
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
  companyId?: number;
  profileId?: number;
  profile?: ImportProfile;
  name: string;
  content: string;
  active?: boolean;
  createdAt?: string;
}

export interface Campaign {
  id?: number;
  companyId?: number;
  profileId?: number;
  templateId?: number;
  phoneVariableId?: number;
  company?: Company;
  profile?: ImportProfile;
  template?: SmsTemplate;
  phoneVariable?: ExcelVariable;
  name: string;
  description?: string;
  channel?: CampaignChannel;
  messageTemplate?: string;
  emailSubject?: string;
  recipientColumn?: string;
  mediaUrl?: string;
  ctaLabel?: string;
  ctaUrl?: string;
  status?: string;
  totalRecipients?: number;
  totalValid?: number;
  totalInvalid?: number;
  totalSegments?: number;
  totalSent?: number;
  totalFailed?: number;
  createdAt?: string;
}

export interface FileAnalysisResponse {
  fileName: string;
  headers: string[];
  detectedPhoneColumn?: string;
  detectedEmailColumn?: string;
  detectedNameColumn?: string;
  totalRows: number;
  sampleRows: Record<string, unknown>[];
  allRows: Record<string, unknown>[];
  headerLabels?: Record<string, string>;
  sheetNames?: string[];
  sheetIndex?: number;
  headerRow?: number;
}

export interface HeaderModelRequest {
  companyId: number;
  name: string;
  description?: string;
  columns: ExcelVariable[];
}

export interface WizardCampaignRequest {
  sheetIndex?: number;
  headerRow?: number;
  profileId?: number;
  columnMapping?: Record<string, string>;
  companyId: number;
  name: string;
  description?: string;
  channel: CampaignChannel;
  messageTemplate: string;
  emailSubject?: string;
  recipientColumn?: string;
  mediaUrl?: string;
  ctaLabel?: string;
  ctaUrl?: string;
  rows: Record<string, unknown>[];
  sendImmediately: boolean;
}

export interface Recipient {
  id?: number;
  campaign?: Campaign;
  phoneNumber?: string;
  email?: string;
  recipientName?: string;
  channel?: CampaignChannel;
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
  channel?: CampaignChannel;
  subject?: string;
  message: string;
  phoneNumbers: string[];
  mediaUrl?: string;
  ctaLabel?: string;
  ctaUrl?: string;
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
export type PaymentMethod =
  | 'WAVE_CI'
  | 'ORANGE_MONEY_CI'
  | 'MTN_MOMO_CI'
  | 'MOOV_MONEY_CI'
  | 'CARD'
  | 'SYSTEM';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';

export interface SmsSendHistory {
  id: number;
  companyId: number;
  companyName: string;
  campaignId?: number;
  campaignName?: string;
  source: SmsSendSource;
  channel?: CampaignChannel;
  phoneNumber: string;
  subject?: string;
  message: string;
  segmentCount: number;
  status: string;
  errorMessage?: string;
  providerResourceUrl?: string;
  providerResourceId?: string;
  deliveryStatus?: string;
  sentAt?: string;
  deliveredAt?: string;
  createdAt?: string;
}

export interface OrangeSmsContract {
  id?: string;
  type?: string;
  country?: string;
  offerName?: string;
  availableUnits?: number;
  requestedUnits?: number;
  status?: string;
  expirationDate?: string;
  creationDate?: string;
  lastUpdateDate?: string;
}

export interface SmsApiDashboard {
  companyId: number;
  companyName: string;
  senderPhone?: string;
  remainingSmsBalance: number;
  totalMessages: number;
  acceptedMessages: number;
  deliveredMessages: number;
  failedMessages: number;
  pendingMessages: number;
  consumedSegments: number;
  failedSegments: number;
  successRate: number;
  lastActivityAt?: string;
  contracts: OrangeSmsContract[];
  recentMessages: SmsSendHistory[];
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
  minRechargeUnits?: number;
  status: SubscriptionStatus;
  startedAt: string;
  expiresAt: string;
}

export interface CompanySubscriptionSummary {
  companyId: number;
  companyName: string;
  smsBalance: number;
  activeSubscription?: CompanySubscription | null;
  lastTransaction?: WalletTransaction | null;
}

export interface WalletTransaction {
  id: number;
  companyId: number;
  companyName: string;
  subscriptionId?: number;
  planName?: string;
  type: WalletTransactionType;
  smsUnits: number;
  unitPrice?: number;
  amount?: number;
  balanceAfter?: number;
  reference?: string;
  paymentMethod?: PaymentMethod;
  paymentStatus?: PaymentStatus;
  payerPhone?: string;
  providerName?: string;
  checkoutUrl?: string;
  reason: string;
  createdAt: string;
}

export interface PriceSimulationResponse {
  smsUnits: number;
  appliedPlanCode: SubscriptionPlanCode;
  appliedPlanName: string;
  unitPrice: number;
  totalAmount: number;
  baseAmount: number;
  savingsAmount: number;
  durationDays: number;
  autoUpgraded: boolean;
}

export interface SubscribePayload {
  planCode: SubscriptionPlanCode;
  smsUnits?: number;
  paymentMethod?: PaymentMethod;
  payerPhone?: string;
  otpCode?: string;
}

export interface RechargeWalletPayload {
  smsUnits: number;
  planCode?: SubscriptionPlanCode;
  paymentMethod?: PaymentMethod;
  payerPhone?: string;
  otpCode?: string;
}
