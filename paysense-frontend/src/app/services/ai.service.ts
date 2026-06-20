import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatRequest {
  message: string;
  conversationId?: string;
}

export interface ChatResponse {
  response: string;
  toolsUsed: string[];
  conversationId: string;
}

export interface ToolDefinition {
  name: string;
  description: string;
  inputSchema: Record<string, any>;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  toolsUsed?: string[];
  timestamp: Date;
}

@Injectable({
  providedIn: 'root'
})
export class AiService {
  private baseUrl = '/api/ai';

  constructor(private http: HttpClient) {}

  /**
   * Send a message to the AI advisor.
   */
  chat(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.baseUrl}/chat`, request);
  }

  /**
   * Get conversation history.
   */
  getHistory(): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.baseUrl}/history`);
  }

  /**
   * Get available MCP tools (public endpoint).
   */
  getTools(): Observable<ToolDefinition[]> {
    return this.http.get<ToolDefinition[]>('/mcp/tools');
  }
}
