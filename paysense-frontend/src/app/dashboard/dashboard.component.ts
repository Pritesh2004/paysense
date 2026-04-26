import { Component, OnInit, OnDestroy, NgZone } from '@angular/core';
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

type ActiveTab = 'wallet';
type ActiveView = 'dashboard' | 'history' | 'notifications';

declare var Razorpay: any;

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
  historyPage = 0;
  historySize = 10;
  historyLastPage = false;
  isLoadingHistoryMore = false;
  
  allNotifications: AppNotification[] = [];

  // Idempotency Keys
  currentWalletPayIdempotencyKey: string = crypto.randomUUID();
  currentTopupIdempotencyKey: string = crypto.randomUUID();
  toasts: AppNotification[] = [];

  activeTab: ActiveTab = 'wallet';
  activeView: ActiveView = 'dashboard';
  historyFilter: 'ALL' | 'SUCCESS' | 'FAILED' = 'ALL';
  showProfileDropdown = false;

  isLoadingAccount = false;
  isLoadingHistory = false;
  isLoadingNotifs = false;

  switchTab(tab: ActiveTab) {
    this.activeTab = tab;
    this.clearMessages();
  }

  switchView(view: ActiveView) {
    this.activeView = view;
    this.clearMessages();
    this.showProfileDropdown = false;
  }

  toggleProfileDropdown() {
    this.showProfileDropdown = !this.showProfileDropdown;
    if (this.showProfileDropdown) {
      this.clearMessages();
    }
  }

  clearMessages() {
    this.walletTopupMessage = '';
    this.walletPayMessage = '';
  }

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
    private authService: AuthService,
    private ngZone: NgZone
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
    this.historyPage = 0;
    this.paymentService.getPaymentHistory(this.historyPage, this.historySize).subscribe({
      next: (historyPageObj) => {
        this.paymentHistory = historyPageObj.content;
        this.historyLastPage = historyPageObj.last;
        this.isLoadingHistory = false;
      },
      error: (err) => {
        console.error('Error fetching payment history', err);
        this.isLoadingHistory = false;
      }
    });
  }

  loadMoreHistory() {
    if (this.historyLastPage || this.isLoadingHistoryMore) return;
    this.isLoadingHistoryMore = true;
    this.historyPage++;
    this.paymentService.getPaymentHistory(this.historyPage, this.historySize).subscribe({
      next: (historyPageObj) => {
        this.paymentHistory = [...this.paymentHistory, ...historyPageObj.content];
        this.historyLastPage = historyPageObj.last;
        this.isLoadingHistoryMore = false;
      },
      error: (err) => {
        console.error('Error fetching more payment history', err);
        this.isLoadingHistoryMore = false;
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

  // ── Wallet Top-Up ──────────────────────────────────────

  topupWallet() {
    if (this.walletTopupAmount < 1 || this.walletTopupAmount > 100000) return;
    this.walletTopupLoading = true;
    this.walletTopupMessage = 'Initializing secure checkout...';

    this.paymentService.createRazorpayOrder(this.walletTopupAmount).subscribe({
      next: (order) => {
        const options = {
          key: order.keyId,
          amount: order.amountInPaise,
          currency: order.currency,
          name: 'PaySense',
          description: 'Wallet Top Up',
          order_id: order.orderId,
          handler: (response: any) => {
            this.ngZone.run(() => {
              this.walletTopupMessage = 'Verifying payment securely...';
              this.paymentService.verifyRazorpayPayment({
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature
              }).subscribe({
                next: () => {
                  this.walletTopupLoading = false;
                  this.walletTopupError = false;
                  this.walletTopupMessage = `✅ Wallet topped up with ₹${this.walletTopupAmount}`;
                  this.walletTopupAmount = 0;
                  this.fetchAccount();
                  this.fetchPaymentHistory();
                },
                error: (err) => {
                  this.walletTopupLoading = false;
                  this.walletTopupError = true;
                  if (err.error?.fieldErrors) {
                    this.walletTopupMessage = Object.values(err.error.fieldErrors).join(', ');
                  } else {
                    this.walletTopupMessage = err.error?.message || 'Payment verification failed.';
                  }
                }
              });
            });
          },
          prefill: {
            name: this.currentUser?.fullName,
            email: this.currentUser?.email,
            contact: this.currentUser?.phone
          },
          theme: {
            color: '#4f46e5'
          },
          modal: {
            ondismiss: () => {
              this.ngZone.run(() => {
                this.walletTopupLoading = false;
                this.walletTopupError = true;
                this.walletTopupMessage = 'Payment cancelled by user.';
                
                // Report failure to backend so the PENDING transaction is marked as FAILED
                this.paymentService.reportRazorpayFailure(order.orderId, 'User cancelled checkout').subscribe({
                  next: () => this.fetchPaymentHistory(),
                  error: (err) => console.error('Failed to report cancellation', err)
                });
              });
            }
          }
        };

        const rzp = new Razorpay(options);

        rzp.on('payment.failed', (response: any) => {
          this.ngZone.run(() => {
            this.paymentService.reportRazorpayFailure(response.error.metadata.order_id, response.error.description).subscribe({
              next: () => {
                this.walletTopupLoading = false;
                this.walletTopupError = true;
                this.walletTopupMessage = `Payment failed: ${response.error.description}`;
                this.fetchPaymentHistory();
              },
              error: () => {
                this.walletTopupLoading = false;
                this.walletTopupError = true;
                this.walletTopupMessage = `Payment failed: ${response.error.description}`;
              }
            });
          });
        });

        rzp.open();
      },
      error: (err) => {
        this.walletTopupLoading = false;
        this.walletTopupError = true;
        if (err.error?.fieldErrors) {
          this.walletTopupMessage = Object.values(err.error.fieldErrors).join(', ');
        } else {
          this.walletTopupMessage = err.error?.message || 'Failed to initialize payment gateway.';
        }
      }
    });
  }

  // ── Wallet Pay ─────────────────────────────────────────

  sendWalletPayment() {
    if (!this.isValidVpa(this.walletPayModel.receiverVpa) || this.walletPayModel.amount < 1 || this.walletPayModel.amount > 10000) return;
    this.walletPayLoading = true;
    this.walletPayMessage = '';

    this.paymentService.walletPay(this.walletPayModel, this.currentWalletPayIdempotencyKey).subscribe({
      next: (res) => {
        this.walletPayLoading = false;
        this.walletPayError = false;
        this.walletPayMessage = `✅ Wallet payment sent! UTR: ${res.utrNumber}`;
        this.walletPayModel = { receiverVpa: '', amount: 0, description: '' };
        this.currentWalletPayIdempotencyKey = crypto.randomUUID(); // Regenerate for the next transaction
        this.fetchAccount();
        this.fetchPaymentHistory();
      },
      error: (err) => {
        this.walletPayLoading = false;
        this.walletPayError = true;
        if (err.error?.fieldErrors) {
          this.walletPayMessage = Object.values(err.error.fieldErrors).join(', ');
        } else {
          this.walletPayMessage = err.error?.message || 'Wallet payment failed.';
        }
      }
    });
  }

  // ── Toast dismiss ──────────────────────────────────────

  dismissToast(notif: AppNotification) {
    this.notificationService.dismissToast(notif.id);
  }

  // ── Auth ───────────────────────────────────────────────

  logout() {
    this.showProfileDropdown = false;
    this.authService.logout().subscribe({
      next: () => {
         this.authService.logoutLocal();
      },
      error: () => {
         this.authService.logoutLocal();
      }
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
    const map: Record<string, string> = { UPI: '📱 UPI', NEFT: '🏦 NEFT', WALLET: '👛 Wallet', RAZORPAY: '💳 Razorpay' };
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
    if (tx.paymentType === 'TOPUP' || tx.paymentType === 'RAZORPAY') return false;
    return tx.senderAccountId === this.accountDetails.id;
  }

  get filteredHistory(): PaymentResponse[] {
    if (this.historyFilter === 'ALL') return this.paymentHistory;
    return this.paymentHistory.filter(tx => tx.status === this.historyFilter);
  }

  get totalTransactions(): number {
    return this.paymentHistory.length;
  }

  get totalSpent(): number {
    return this.paymentHistory
      .filter(tx => this.isSender(tx) && tx.status === 'SUCCESS')
      .reduce((sum, tx) => sum + tx.amount, 0);
  }

  get totalReceived(): number {
    return this.paymentHistory
      .filter(tx => !this.isSender(tx) && tx.status === 'SUCCESS')
      .reduce((sum, tx) => sum + tx.amount, 0);
  }

  isValidVpa(vpa: string): boolean {
    if (!vpa) return false;
    return /^[\w.-]+@[\w.-]+$/.test(vpa);
  }
}

