export class AppError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function assert(condition: unknown, status: number, code: string, message: string): asserts condition {
  if (!condition) {
    throw new AppError(status, code, message);
  }
}

export function notFound(message = "Resource not found."): never {
  throw new AppError(404, "NOT_FOUND", message);
}

export function forbidden(message = "You do not have access to this resource."): never {
  throw new AppError(403, "FORBIDDEN", message);
}

