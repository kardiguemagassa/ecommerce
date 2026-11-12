import { fr } from '../i18n/fr';

/** RFC 9457 problem details, as the API sends them (application/problem+json). */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Stable code when the status alone is ambiguous: UNAUTHENTICATED, IMAGE_TOO_LARGE... */
  code?: string;
  /** Trace of the request: the user quotes it to the support, who finds the request in Grafana. */
  traceId?: string;
  errors?: Record<string, string>;
}

/** A failed API call: HTTP status, the problem sent by the API (if any) and the message to show, in French. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    super(messageOf(status, problem));
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get traceId(): string | undefined {
    return this.problem.traceId;
  }
}

const byCode: Record<string, string> = {
  UNAUTHENTICATED: fr.errors.unauthenticated,
  ACCESS_DENIED: fr.errors.accessDenied,
  IMAGE_TOO_LARGE: fr.errors.imageTooLarge,
  UNSUPPORTED_IMAGE: fr.errors.unsupportedImage,
  INVALID_IMAGE: fr.errors.invalidImage,
  IMAGE_LIMIT: fr.errors.imageLimit,
  REVIEW_NOT_ALLOWED: fr.errors.reviewNotAllowed,
  STORAGE_UNAVAILABLE: fr.errors.storageUnavailable,
  RATE_LIMITED: fr.errors.rateLimited,
};

// The API words its titles in English; the interface never shows them as they are.
const conflictByTitle: Record<string, string> = {
  'Insufficient stock': fr.errors.insufficientStock,
  'Resource already exists': fr.errors.duplicate,
  'Resource in use': fr.errors.inUse,
  'Data integrity violation': fr.errors.linked,
  'Empty cart': fr.errors.emptyCart,
  'Concurrent modification': fr.errors.concurrentModification,
};

export function messageOf(status: number, problem: Problem): string {
  if (problem.code && byCode[problem.code]) return byCode[problem.code]!;
  if (status === 401) return fr.errors.unauthenticated;
  if (status === 403) return fr.errors.accessDenied;
  if (status === 404) return fr.errors.notFound;
  if (status === 400) return fr.errors.invalid;
  if (status === 413) return fr.errors.imageTooLarge;
  if (status === 429) return fr.errors.rateLimited;
  if (status === 409) return conflictByTitle[problem.title ?? ''] ?? fr.errors.conflict;
  if (status === 0 || status >= 500) return fr.errors.unavailable;
  return fr.errors.unexpected;
}

/** The body of an openapi-fetch error, whatever the endpoint: a problem when the API sent one. */
export function toProblem(error: unknown): Problem {
  return error && typeof error === 'object' ? (error as Problem) : {};
}

/** Returns the data of an openapi-fetch call, or throws an ApiError carrying the problem of the API. */
export async function unwrap<T>(call: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  let result: { data?: T; error?: unknown; response: Response };
  try {
    result = await call;
  } catch {
    throw new ApiError(0, {}); // network failure: API unreachable
  }
  if (!result.response.ok) {
    throw new ApiError(result.response.status, toProblem(result.error));
  }
  return result.data as T;
}
