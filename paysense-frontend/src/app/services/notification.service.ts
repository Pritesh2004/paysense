import { Injectable, NgZone } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  channel: string;
  isRead: boolean;
  sentAt?: string;
  readAt?: string;
  timestamp: Date;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private eventSource!: EventSource;
  private notificationsSubject = new BehaviorSubject<AppNotification[]>([]);
  public notifications$ = this.notificationsSubject.asObservable();

  private currentNotifications: AppNotification[] = [];

  // Live toast overlay (max 5)
  private toastsSubject = new BehaviorSubject<AppNotification[]>([]);
  public toasts$ = this.toastsSubject.asObservable();
  private activeToasts: AppNotification[] = [];

  constructor(private zone: NgZone, private http: HttpClient) {}

  // ── REST calls ──────────────────────────────────────────

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${localStorage.getItem('paysense_access_token')}`
    });
  }

  /** Load historical notifications from the DB on page load */
  getNotifications(): Observable<AppNotification[]> {
    const userId = localStorage.getItem('userId');
    return this.http.get<AppNotification[]>(
      `${environment.notificationApi}/notifications/${userId}`,
      { headers: this.getHeaders() }
    );
  }

  /** Mark a notification as read */
  markAsRead(notificationId: string): Observable<void> {
    return this.http.patch<void>(
      `${environment.notificationApi}/notifications/${notificationId}/read`,
      {},
      { headers: this.getHeaders() }
    );
  }

  /** Merge loaded history into the notifications$ observable */
  loadHistory(notifications: AppNotification[]): void {
    this.currentNotifications = notifications.map(n => ({ ...n, timestamp: new Date(n.sentAt || Date.now()) }));
    this.notificationsSubject.next(this.currentNotifications);
  }

  // ── SSE connection ───────────────────────────────────────

  connect() {
    const userId = localStorage.getItem('userId');
    if (!userId) return;

    this.eventSource = new EventSource(
      `${environment.notificationApi}/notifications/${userId}/stream`
    );

    // Initial connection confirmation
    this.eventSource.onmessage = (event) => {
      console.log('SSE Message:', event.data);
    };

    // Typed event listeners
    this.eventSource.addEventListener('PAYMENT_SUCCESS', (event: any) => {
      this.handleEventData(event);
    });

    this.eventSource.addEventListener('PAYMENT_FAILED', (event: any) => {
      this.handleEventData(event);
    });

    this.eventSource.addEventListener('FRAUD_ALERT', (event: any) => {
      this.handleEventData(event);
    });

    this.eventSource.onerror = (error) => {
      console.error('SSE Error:', error);
    };
  }

  private handleEventData(event: any) {
    this.zone.run(() => {
      try {
        const data = JSON.parse(event.data);
        const newNotif: AppNotification = {
          id: data.id,
          type: data.type,
          title: data.title,
          body: data.body,
          channel: data.channel,
          isRead: data.isRead,
          timestamp: new Date()
        };

        // Prepend to full notification list
        this.currentNotifications = [newNotif, ...this.currentNotifications];
        this.notificationsSubject.next(this.currentNotifications);

        // Also push as toast
        this.activeToasts = [newNotif, ...this.activeToasts].slice(0, 5);
        this.toastsSubject.next(this.activeToasts);

        // Auto-dismiss toast after 8s
        setTimeout(() => {
          this.activeToasts = this.activeToasts.filter(t => t.id !== newNotif.id);
          this.toastsSubject.next(this.activeToasts);
        }, 8000);

      } catch (e) {
        console.error('Failed to parse SSE event data', e);
      }
    });
  }

  dismissToast(notifId: string): void {
    this.activeToasts = this.activeToasts.filter(t => t.id !== notifId);
    this.toastsSubject.next(this.activeToasts);
  }

  disconnect() {
    if (this.eventSource) {
      this.eventSource.close();
    }
  }
}

