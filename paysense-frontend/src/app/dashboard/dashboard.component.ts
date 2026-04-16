import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  PaymentService, AccountResponse, PaymentResponse,
  UpiPaymentRequest, NeftPaymentRequest
} from '../services/payment.service';
import { NotificationService, AppNotification } from '../services/notification.service';
import { AuthService, User } from '../auth/auth.service';
import { Subscription } from 'rxjs';

type ActiveTab = 'upi' | 'neft' | 'wallet';
type ActiveView = 'dashboard' | 'history' | 'notifications';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {

  // ── State ──────────────────────────────────────────────
  currentUser: User | null = null;
  accountDetails: AccountResponse | null = null;
  paymentHistory: PaymentResponse[] = [];
  allNotifications: AppNotification[] = [];
  toasts: AppNotification[] = [];

  activeTab: ActiveTab = 'upi';
  activeView: ActiveView = 'dashboard';

  isLoadingAccount = false;
  isLoadingHistory = false;
  isLoadingNotifs = false;

  switchTab(tab: ActiveTab) {
    this.activeTab = tab;
    this.upiMessage = '';
    this.neftMessage = '';
    this.walletTopupMessage = '';
    this.walletPayMessage = '';
  }

  // ── UPI Form ───────────────────────────────────────────
  upiModel: UpiPaymentRequest = { receiverVpa: '', amount: 0, description: '' };
  upiLoading = false;
  upiMessage = '';
  upiError = false;

  // ── NEFT Form ──────────────────────────────────────────
  neftModel: NeftPaymentRequest = { receiverAccountNo: '', receiverIfsc: 'PAYS0000001', amount: 0, description: '' };
  neftLoading = false;
  neftMessage = '';
  neftError = false;

  // ── Wallet Forms ───────────────────────────────────────
  walletTopupAmount = 0;
  walletTopupLoading = false;
  walletTopupMessage = '';
  walletTopupError = false;

  walletPayModel = { receiverVpa: '', amount: 0, description: '' };
  walletPayLoading = false;
  walletPayMessage = '';
  walletPayError = false;

  private subscriptions = new Subscription();

  constructor(
    private router: Router,
    private paymentService: PaymentService,
    private notificationService: NotificationService,
    private authService: AuthService
  ) {}

  ngOnInit() {
    // Subscribe to current user
    this.subscriptions.add(
      this.authService.currentUser$.subscribe(user => this.currentUser = user)
    );

    // Subscribe to SSE toast stream
    this.subscriptions.add(
      this.notificationService.toasts$.subscribe(toasts => this.toasts = toasts)
    );

    // Subscribe to full notification list
    this.subscriptions.add(
      this.notificationService.notifications$.subscribe(notifs => this.allNotifications = notifs)
    );

    // Load all data
    this.fetchAccount();
    this.fetchPaymentHistory();
    this.fetchNotifications();

    // Connect real-time SSE
    this.notificationService.connect();
  }

  ngOnDestroy() {
    this.notificationService.disconnect();
    this.subscriptions.unsubscribe();
  }

  // ── Account ────────────────────────────────────────────

  fetchAccount() {
    this.isLoadingAccount = true;
    this.paymentService.getAccountDetails().subscribe({
      next: (data) => {
        this.accountDetails = data;
        this.isLoadingAccount = false;
      },
      error: (err) => {
        console.error('Error fetching account', err);
        this.isLoadingAccount = false;
      }
    });
  }

  // ── Payment History ────────────────────────────────────

  fetchPaymentHistory() {
    this.isLoadingHistory = true;
    this.paymentService.getPaymentHistory().subscribe({
      next: (history) => {
        this.paymentHistory = history;
        this.isLoadingHistory = false;
      },
      error: (err) => {
        console.error('Error fetching payment history', err);
        this.isLoadingHistory = false;
      }
    });
  }

  // ── Notifications ───────────────────────────────────────

  fetchNotifications() {
    this.isLoadingNotifs = true;
    this.notificationService.getNotifications().subscribe({
      next: (notifs) => {
        this.notificationService.loadHistory(notifs);
        this.isLoadingNotifs = false;
      },
      error: (err) => {
        console.error('Error fetching notifications', err);
        this.isLoadingNotifs = false;
      }
    });
  }

  markNotificationRead(notif: AppNotification) {
    if (notif.isRead) return;
    this.notificationService.markAsRead(notif.id).subscribe({
      next: () => {
        notif.isRead = true;
      },
      error: (err) => console.error('Failed to mark as read', err)
    });
  }

  get unreadCount(): number {
    return this.allNotifications.filter(n => !n.isRead).length;
  }

  // ── UPI Payment ────────────────────────────────────────

  sendUpiPayment() {
    if (!this.upiModel.receiverVpa || this.upiModel.amount <= 0) return;
    this.upiLoading = true;
    this.upiMessage = '';

    this.paymentService.sendUpiPayment(this.upiModel).subscribe({
      next: (res) => {
        this.upiLoading = false;
        this.upiError = false;
        this.upiMessage = `✅ Payment successful! UTR: ${res.utrNumber}`;
        this.fetchAccount();
        this.fetchPaymentHistory();
        this.upiModel = { receiverVpa: '', amount: 0, description: '' };
      },
      error: (err) => {
        this.upiLoading = false;
        this.upiError = true;
        this.upiMessage = err.error?.failureReason
          ? `Failed: ${err.error.failureReason}`
          : (err.error?.message || 'Payment blocked or failed.');
      }
    });
  }

  // ── NEFT Payment ───────────────────────────────────────

  sendNeftPayment() {
    if (!this.neftModel.receiverAccountNo || this.neftModel.amount <= 0) return;
    this.neftLoading = true;
    this.neftMessage = '';

    this.paymentService.sendNeftPayment(this.neftModel).subscribe({
      next: (res) => {
        this.neftLoading = false;
        this.neftError = false;
        this.neftMessage = `✅ NEFT Initiated! UTR: ${res.utrNumber}. Settlement within 30 min.`;
        this.fetchAccount();
        this.fetchPaymentHistory();
        this.neftModel = { receiverAccountNo: '', receiverIfsc: 'PAYS0000001', amount: 0, description: '' };
      },
      error: (err) => {
        this.neftLoading = false;
        this.neftError = true;
        this.neftMessage = err.error?.message || 'NEFT payment failed.';
      }
    });
  }

  // ── Wallet Top-Up ──────────────────────────────────────

  topupWallet() {
    if (this.walletTopupAmount <= 0) return;
    this.walletTopupLoading = true;
    this.walletTopupMessage = '';

    this.paymentService.topupWallet(this.walletTopupAmount).subscribe({
      next: () => {
        this.walletTopupLoading = false;
        this.walletTopupError = false;
        this.walletTopupMessage = `✅ Wallet topped up with ₹${this.walletTopupAmount}`;
        this.walletTopupAmount = 0;
        this.fetchAccount();
      },
      error: (err) => {
        this.walletTopupLoading = false;
        this.walletTopupError = true;
        this.walletTopupMessage = err.error?.message || 'Wallet top-up failed.';
      }
    });
  }

  // ── Wallet Pay ─────────────────────────────────────────

  sendWalletPayment() {
    if (!this.walletPayModel.receiverVpa || this.walletPayModel.amount <= 0) return;
    this.walletPayLoading = true;
    this.walletPayMessage = '';

    this.paymentService.walletPay(this.walletPayModel).subscribe({
      next: (res) => {
        this.walletPayLoading = false;
        this.walletPayError = false;
        this.walletPayMessage = `✅ Wallet payment sent! UTR: ${res.utrNumber}`;
        this.walletPayModel = { receiverVpa: '', amount: 0, description: '' };
        this.fetchAccount();
        this.fetchPaymentHistory();
      },
      error: (err) => {
        this.walletPayLoading = false;
        this.walletPayError = true;
        this.walletPayMessage = err.error?.message || 'Wallet payment failed.';
      }
    });
  }

  // ── Toast dismiss ──────────────────────────────────────

  dismissToast(notif: AppNotification) {
    this.notificationService.dismissToast(notif.id);
  }

  // ── Auth ───────────────────────────────────────────────

  logout() {
    this.authService.logout().subscribe({
      next: () => {},
      error: () => {}
    });
  }

  // ── Helpers ────────────────────────────────────────────

  getStatusClass(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'text-green-600 bg-green-50';
      case 'PENDING': return 'text-yellow-600 bg-yellow-50';
      case 'FAILED': return 'text-red-600 bg-red-50';
      default: return 'text-slate-600 bg-slate-50';
    }
  }

  getPaymentTypeLabel(type: string): string {
    const map: Record<string, string> = { UPI: '📱 UPI', NEFT: '🏦 NEFT', WALLET: '👛 Wallet' };
    return map[type] || type;
  }

  getUserInitial(): string {
    return this.currentUser?.fullName?.charAt(0)?.toUpperCase() || 'U';
  }

  getUserName(): string {
    return this.currentUser?.fullName || 'User';
  }

  isSender(tx: PaymentResponse): boolean {
    if (!this.accountDetails) return false;
    return tx.senderAccountId === this.accountDetails.id;
  }
}

