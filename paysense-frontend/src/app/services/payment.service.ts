import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

// ── Interfaces matching backend DTOs ─────────────────────

export interface WalletInfo {
  id: string;
  balance: number;
  dailyLimit: number;
  todaySpent: number;
  remainingDailyLimit: number;
  status: string;
}

export interface AccountResponse {
  id: string;
  userId: string;
  accountNumber: string;
  ifscCode: string;
  balance: number;
  accountType: string;
  status: string;
  createdAt: string;
  wallet: WalletInfo;
  vpas: string[];
}

export interface UpiPaymentRequest {
  receiverVpa: string;
  amount: number;
  description: string;
}

export interface NeftPaymentRequest {
  receiverAccountNo: string;
  receiverIfsc: string;
  amount: number;
  description: string;
}

export interface WalletTopupRequest {
  amount: number;
}

export interface WalletPayRequest {
  receiverVpa: string;
  amount: number;
  description: string;
}

export interface PaymentResponse {
  paymentRequestId: string;
  idempotencyKey: string;
  utrNumber: string;
  amount: number;
  paymentType: string;
  status: string;
  failureReason: string;
  description: string;
  initiatedAt: string;
  settledAt: string;
  message: string;
}

export interface AdminTopupRequest {
  userId: string;
  amount: number;
}

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = environment.paymentApi;

  constructor(private http: HttpClient) {}

  // ── Headers ────────────────────────────────────────────

  private getAuthHeaders(): HttpHeaders {
    const userId = localStorage.getItem('userId') || '';
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'X-User-Id': userId,
      'Authorization': `Bearer ${localStorage.getItem('paysense_access_token')}`
    });
  }

  private getIdempotentHeaders(): HttpHeaders {
    return this.getAuthHeaders().set('X-Idempotency-Key', crypto.randomUUID());
  }

  // ── Account ─────────────────────────────────────────────

  getAccountDetails(): Observable<AccountResponse> {
    const userId = localStorage.getItem('userId');
    return this.http.get<AccountResponse>(`${this.apiUrl}/payments/accounts/${userId}`, {
      headers: this.getAuthHeaders()
    });
  }

  adminTopup(userId: string, amount: number): Observable<AccountResponse> {
    return this.http.post<AccountResponse>(`${this.apiUrl}/payments/accounts/admin/topup`,
      { userId, amount },
      { headers: this.getAuthHeaders() }
    );
  }

  // ── UPI ─────────────────────────────────────────────────

  sendUpiPayment(request: UpiPaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/upi`, request, {
      headers: this.getIdempotentHeaders()
    });
  }

  // ── NEFT ────────────────────────────────────────────────

  sendNeftPayment(request: NeftPaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/neft`, request, {
      headers: this.getIdempotentHeaders()
    });
  }

  // ── Wallet ──────────────────────────────────────────────

  topupWallet(amount: number): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/wallet/topup`,
      { amount } as WalletTopupRequest,
      { headers: this.getIdempotentHeaders() }
    );
  }

  walletPay(request: WalletPayRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/wallet/pay`, request, {
      headers: this.getIdempotentHeaders()
    });
  }

  // ── History ─────────────────────────────────────────────

  getPaymentHistory(): Observable<PaymentResponse[]> {
    return this.http.get<PaymentResponse[]>(`${this.apiUrl}/payments/history`, {
      headers: this.getAuthHeaders()
    });
  }

  getPaymentById(paymentId: string): Observable<PaymentResponse> {
    return this.http.get<PaymentResponse>(`${this.apiUrl}/payments/${paymentId}`, {
      headers: this.getAuthHeaders()
    });
  }
}

