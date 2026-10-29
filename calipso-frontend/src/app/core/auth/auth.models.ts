import { CampaignChannel, CompanyUserRole } from '../models/api.models';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SetupAdminRequest {
  companyName: string;
  fullName: string;
  username: string;
  email: string;
  password: string;
}

export interface ForgotPasswordRequest {
  identifier: string;
  channel: CampaignChannel;
}

export interface ForgotPasswordResponse {
  message: string;
  maskedDestination: string;
  channel: CampaignChannel;
  devCodeHint?: string;
}

export interface ResetPasswordRequest {
  identifier: string;
  code: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthUser {
  platformAdmin?: boolean;
  id: number;
  companyId: number;
  companyName: string;
  fullName: string;
  username: string;
  email: string;
  role: CompanyUserRole;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: AuthUser;
}
