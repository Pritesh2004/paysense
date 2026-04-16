import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError, tap, catchError, switchMap, of } from 'rxjs';
import { Router } from '@angular/router';

export interface User {
  id: string;
  email: string;
  fullName: string;
  phone: string;
  role: string;
  isVerified: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = '/api/auth';
  private readonly ACCESS_TOKEN_KEY = 'paysense_access_token';
  private readonly REFRESH_TOKEN_KEY = 'paysense_refresh_token';
  private accessToken: string | null = null;
  private refreshTokenVal: string | null = null;
  
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {
    this.accessToken = localStorage.getItem(this.ACCESS_TOKEN_KEY);
    this.refreshTokenVal = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    if (this.accessToken) {
      setTimeout(() => {
        this.fetchCurrentUser().subscribe({
          error: () => this.refreshToken().subscribe({
            error: () => this.logoutLocal()
          })
        });
      }, 0);
    }
  }

  public get token(): string | null {
    return this.accessToken;
  }

  public get currentRefreshToken(): string | null {
    return this.refreshTokenVal;
  }

  register(data: any): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.API_URL}/register`, data).pipe(
      tap(res => this.setSession(res)),
      catchError(this.handleError)
    );
  }

  login(credentials: any): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.API_URL}/login`, credentials).pipe(
      tap(res => this.setSession(res)),
      switchMap(() => this.fetchCurrentUser()),
      catchError(this.handleError)
    );
  }

  refreshToken(): Observable<TokenResponse> {
    const rToken = this.refreshTokenVal;
    if (!rToken) {
      this.logoutLocal();
      return throwError(() => new Error('No refresh token available'));
    }
    
    // Explicitly using query string parameter since the backend expects @RequestParam("token")
    return this.http.post<TokenResponse>(`${this.API_URL}/refresh?token=${encodeURIComponent(rToken)}`, {}).pipe(
      tap(res => {
        this.setSession(res);
      }),
      catchError((err) => {
        this.logoutLocal();
        return throwError(() => err);
      })
    );
  }

  logout(): Observable<any> {
    const rToken = this.refreshTokenVal;
    
    if (!rToken) {
      this.logoutLocal();
      return of(null);
    }
    
    return this.http.post(`${this.API_URL}/logout?token=${encodeURIComponent(rToken)}`, {}).pipe(
      tap(() => this.logoutLocal()),
      catchError(err => {
        this.logoutLocal();
        return this.handleError(err);
      })
    );
  }

  public logoutLocal(): void {
    console.error('logoutLocal() called! Preventing aggressive logout. Check network tab to see what API failed.');
    // this.accessToken = null;
    // this.refreshTokenVal = null;
    // localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    // localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    // this.currentUserSubject.next(null);
    // this.router.navigate(['/login']);
  }

  private fetchCurrentUser(): Observable<any> {
    return this.http.get<User>(`${this.API_URL}/me`).pipe(
      tap(user => this.currentUserSubject.next(user))
    );
  }

  private setSession(authResult: TokenResponse): void {
    this.accessToken = authResult.accessToken;
    if (authResult.refreshToken) {
      this.refreshTokenVal = authResult.refreshToken;
      localStorage.setItem(this.REFRESH_TOKEN_KEY, authResult.refreshToken);
    }
    localStorage.setItem(this.ACCESS_TOKEN_KEY, authResult.accessToken);
    if (authResult.userId) {
        localStorage.setItem('userId', authResult.userId);
    }
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'An unknown error occurred!';
    if (error.error instanceof ErrorEvent) {
      // Client-side errors
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side errors
      if (error.error && error.error.message) {
        errorMessage = error.error.message;
      } else {
        errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
      }
    }
    return throwError(() => new Error(errorMessage));
  }
}
