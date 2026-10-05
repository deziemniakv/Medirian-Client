/** Small HTTP helpers on top of fetch with timeouts and retries. */

export class HttpError extends Error {
  constructor(public readonly url: string, public readonly status: number, message?: string) {
    super(message ?? `HTTP ${status} for ${url}`);
  }
}

export interface RequestOptions {
  timeoutMs?: number;
  retries?: number;
  init?: RequestInit;
}

export const USER_AGENT = 'MedirianLauncher/0.1 (+https://github.com/medirian-client)';

export async function request(url: string, { timeoutMs = 20_000, retries = 2, init }: RequestOptions = {}): Promise<Response> {
  let lastError: unknown;
  for (let attempt = 0; attempt <= retries; attempt++) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, {
        ...init,
        headers: { 'User-Agent': USER_AGENT, ...(init?.headers ?? {}) },
        signal: controller.signal
      });
      if (!response.ok) {
        // 4xx are not retried (except 429)
        if (response.status < 500 && response.status !== 429) {
          throw new HttpError(url, response.status, await safeText(response));
        }
        throw new HttpError(url, response.status);
      }
      return response;
    } catch (error) {
      lastError = error;
      if (error instanceof HttpError && error.status < 500 && error.status !== 429) {
        throw error;
      }
      if (attempt < retries) {
        await new Promise((resolve) => setTimeout(resolve, 500 * 2 ** attempt));
      }
    } finally {
      clearTimeout(timer);
    }
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

async function safeText(response: Response): Promise<string> {
  try {
    return (await response.text()).slice(0, 500);
  } catch {
    return '';
  }
}

export async function fetchJson<T>(url: string, options?: RequestOptions): Promise<T> {
  const response = await request(url, options);
  return (await response.json()) as T;
}

export async function fetchText(url: string, options?: RequestOptions): Promise<string> {
  const response = await request(url, options);
  return response.text();
}

/** POST helper for JSON and form bodies (used by Microsoft authentication). */
export async function postJson<T>(url: string, body: unknown, form = false): Promise<T> {
  const response = await request(url, {
    retries: 0,
    init: {
      method: 'POST',
      headers: form
        ? { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' }
        : { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: form ? new URLSearchParams(body as Record<string, string>).toString() : JSON.stringify(body)
    }
  });
  return (await response.json()) as T;
}
