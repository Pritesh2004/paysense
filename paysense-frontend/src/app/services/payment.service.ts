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
  senderAccountId: string;
  senderVpa?: string;
  receiverVpa?: string;
}

export interface PaginatedPaymentResponse {
  content: PaymentResponse[];
  pageable: any;
  last: boolean;
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface AdminTopupRequest {
  userId: string;
  amount: number;
}

export interface RazorpayOrderResponse {
  orderId: string;
  amountInPaise: number;
  currency: string;
  keyId: string;
  transactionId: string;
}

export interface RazorpayVerifyRequest {
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
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

  sendUpiPayment(request: UpiPaymentRequest, idempotencyKey: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/upi`, request, {
      headers: this.getAuthHeaders().set('X-Idempotency-Key', idempotencyKey)
    });
  }

  // ── NEFT ────────────────────────────────────────────────

  sendNeftPayment(request: NeftPaymentRequest, idempotencyKey: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/neft`, request, {
      headers: this.getAuthHeaders().set('X-Idempotency-Key', idempotencyKey)
    });
  }

  // ── Wallet ──────────────────────────────────────────────

  topupWallet(amount: number, idempotencyKey: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/wallet/topup`,
      { amount } as WalletTopupRequest,
      { headers: this.getAuthHeaders().set('X-Idempotency-Key', idempotencyKey) }
    );
  }

  walletPay(request: WalletPayRequest, idempotencyKey: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/wallet/pay`, request, {
      headers: this.getAuthHeaders().set('X-Idempotency-Key', idempotencyKey)
    });
  }

  // ── Razorpay ────────────────────────────────────────────

  createRazorpayOrder(amount: number): Observable<RazorpayOrderResponse> {
    return this.http.post<RazorpayOrderResponse>(`${this.apiUrl}/payments/razorpay/create-order`,
      { amount },
      { headers: this.getAuthHeaders() }
    );
  }

  verifyRazorpayPayment(data: RazorpayVerifyRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/razorpay/verify`, data, {
      headers: this.getAuthHeaders()
    });
  }

  reportRazorpayFailure(orderId: string, reason: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/payments/razorpay/failure`,
      { orderId, reason },
      { headers: this.getAuthHeaders() }
    );
  }

  // ── History ─────────────────────────────────────────────

  getPaymentHistory(page: number = 0, size: number = 10): Observable<PaginatedPaymentResponse> {
    return this.http.get<PaginatedPaymentResponse>(`${this.apiUrl}/payments/history?page=${page}&size=${size}`, {
      headers: this.getAuthHeaders()
    });
  }

  getPaymentById(paymentId: string): Observable<PaymentResponse> {
    return this.http.get<PaymentResponse>(`${this.apiUrl}/payments/${paymentId}`, {
      headers: this.getAuthHeaders()
    });
  }
}

