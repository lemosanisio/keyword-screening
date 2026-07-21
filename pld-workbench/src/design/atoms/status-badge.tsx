import { Badge } from "@/components/ui/badge";
import type { CaseStatus } from "@/api/types";

export function StatusBadge({ status }: { status: CaseStatus }) {
  const variant = status === "PENDING_APPROVAL" ? "warning" : status === "DECIDED" ? "success" : "secondary";
  return <Badge variant={variant}>{status}</Badge>;
}
