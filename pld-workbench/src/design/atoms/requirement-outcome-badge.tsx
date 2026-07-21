import { Badge } from "@/components/ui/badge";
import type { EvidenceRequirement } from "@/api/types";

export function RequirementOutcomeBadge({ outcome }: { outcome: EvidenceRequirement["outcome"] }) {
  const variant = outcome === "SATISFIED" || outcome === "WAIVED" ? "success" : outcome === "TECHNICAL_PENDING" || outcome === "PENDING" ? "warning" : "destructive";
  return <Badge variant={variant}>{outcome}</Badge>;
}
