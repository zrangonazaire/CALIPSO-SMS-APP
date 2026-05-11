import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../api';
import { ExcelVariable } from '../models/api.models';

@Injectable({
  providedIn: 'root',
})
export class ExcelVariableService {
  private readonly apiUrl = `${API_BASE}/excel-variables`;

  constructor(private http: HttpClient) {}

  findByProfile(profileId: number): Observable<ExcelVariable[]> {
    return this.http.get<ExcelVariable[]>(`${this.apiUrl}/profile/${profileId}`);
  }

  create(variable: ExcelVariable): Observable<ExcelVariable> {
    return this.http.post<ExcelVariable>(this.apiUrl, variable);
  }

  delete(variableId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${variableId}`);
  }
}
