export function correlationId(): string {
  return `ui-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
