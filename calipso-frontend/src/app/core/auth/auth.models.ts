import { CompanyUserRole } from '../models/api.models';

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

export interface AuthUser {
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
