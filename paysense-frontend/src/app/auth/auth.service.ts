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
  // refresh token is stored in HttpOnly cookie by the server
  userId: string;
  email: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = '/api/auth';
  private accessToken: string | null = null;
  
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {}

  public get token(): string | null {
    return this.accessToken;
  }

  register(data: any): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.API_URL}/register`, data).pipe(
      tap(res => this.setSession(res)),
      catchError(this.handleError)
    );
  }

  login(credentials: any): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.API_URL}/login`, credentials, { withCredentials: true }).pipe(
      tap(res => this.setSession(res)),
      switchMap(() => this.fetchCurrentUser()),
      catchError(this.handleError)
    );
  }

  refreshToken(): Observable<TokenResponse> {
    // The server will read the required HttpOnly cookie from the request
    return this.http.post<TokenResponse>(`${this.API_URL}/refresh`, {}, { withCredentials: true }).pipe(
      tap(res => {
        this.accessToken = res.accessToken;
      }),
      catchError((err) => {
        this.logoutLocal();
        return throwError(() => err);
      })
    );
  }

  logout(): Observable<any> {
    return this.http.post(`${this.API_URL}/logout`, {}, { withCredentials: true }).pipe(
      tap(() => this.logoutLocal()),
      catchError(this.handleError)
    );
  }

  public logoutLocal(): void {
    this.accessToken = null;
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  private fetchCurrentUser(): Observable<any> {
    return this.http.get<User>(`${this.API_URL}/me`).pipe(
      tap(user => this.currentUserSubject.next(user))
    );
  }

  private setSession(authResult: TokenResponse): void {
    this.accessToken = authResult.accessToken;
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
