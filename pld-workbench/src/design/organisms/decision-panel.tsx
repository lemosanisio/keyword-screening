import * as React from "react";
import type { DecisionCommand, DecisionView } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { DecisionStatus } from "@/design/molecules/decision-status";

const accountDecisionOptions = [
  "MAINTAIN",
  "RESTRICT",
  "SUSPEND",
  "TERMINATE_RELATIONSHIP",
  "REQUEST_INFORMATION"
];

const suspicionDecisionOptions = ["NO_SUSPICION", "KEEP_MONITORING", "COMMUNICATE_TO_COAF", "INCONCLUSIVE"];

type DecisionPanelProps = {
  caseVersion: number;
  accountDecisions: DecisionView[];
  suspicionDecisions: DecisionView[];
  onAccountDecision: (command: DecisionCommand) => void;
  onSuspicionDecision: (command: DecisionCommand) => void;
  busy: boolean;
  decisionAllowed: boolean;
  blockingReasons: string[];
};

export function DecisionPanel(props: DecisionPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Painel de decisão</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {!props.decisionAllowed && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-950">
            Decisão bloqueada por requisitos obrigatórios: {props.blockingReasons.join(", ")}
          </div>
        )}
        <DecisionForm
          title="Relacionamento"
          options={accountDecisionOptions}
          defaultDecision="MAINTAIN"
          defaultPolicy="account-decision-policy-1"
          caseVersion={props.caseVersion}
          busy={props.busy}
          disabled={!props.decisionAllowed}
          onSubmit={props.onAccountDecision}
        />
        <Separator />
        <DecisionForm
          title="Suspeição"
          options={suspicionDecisionOptions}
          defaultDecision="NO_SUSPICION"
          defaultPolicy="suspicion-policy-1"
          caseVersion={props.caseVersion}
          busy={props.busy}
          disabled={!props.decisionAllowed}
          onSubmit={props.onSuspicionDecision}
        />
        <Separator />
        <div className="space-y-2">
          <h4 className="text-xs font-semibold uppercase text-muted-foreground">Decisões registradas</h4>
          {[...props.accountDecisions, ...props.suspicionDecisions].length === 0 && (
            <p className="text-sm text-muted-foreground">Nenhuma decisão registrada.</p>
          )}
          {props.accountDecisions.map((decision) => <DecisionStatus key={decision.decisionId} decision={decision} />)}
          {props.suspicionDecisions.map((decision) => <DecisionStatus key={decision.decisionId} decision={decision} />)}
        </div>
      </CardContent>
    </Card>
  );
}

function DecisionForm({
  title,
  options,
  defaultDecision,
  defaultPolicy,
  caseVersion,
  busy,
  disabled,
  onSubmit
}: {
  title: string;
  options: string[];
  defaultDecision: string;
  defaultPolicy: string;
  caseVersion: number;
  busy: boolean;
  disabled: boolean;
  onSubmit: (command: DecisionCommand) => void;
}) {
  const [decision, setDecision] = React.useState(defaultDecision);
  const [reasonCodes, setReasonCodes] = React.useState("TRANSACTION_SIGNAL_REVIEWED");
  const [narrative, setNarrative] = React.useState("");
  const [policyVersion, setPolicyVersion] = React.useState(defaultPolicy);

  const sensitive = decision === "TERMINATE_RELATIONSHIP" || decision === "SUSPEND" || decision === "RESTRICT" || decision === "COMMUNICATE_TO_COAF";

  return (
    <div className="space-y-3">
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        {sensitive && <p className="mt-1 text-xs text-amber-700">Ação sensível: deve gerar pendência para segundo aprovador.</p>}
      </div>
      <div className="grid gap-2 md:grid-cols-2">
        <Select value={decision} onChange={(event) => setDecision(event.target.value)}>
          {options.map((option) => <option key={option} value={option}>{option}</option>)}
        </Select>
        <Input value={policyVersion} onChange={(event) => setPolicyVersion(event.target.value)} placeholder="policyVersion" />
      </div>
      <Input value={reasonCodes} onChange={(event) => setReasonCodes(event.target.value)} placeholder="Reason codes separados por vírgula" />
      <Textarea value={narrative} onChange={(event) => setNarrative(event.target.value)} placeholder="Narrativa da decisão" />
      <Button
        size="sm"
        disabled={busy || disabled || narrative.trim().length === 0}
        onClick={() => onSubmit({
          expectedVersion: caseVersion,
          decision,
          reasonCodes: reasonCodes.split(",").map((code) => code.trim()).filter(Boolean),
          narrative,
          policyVersion
        })}
      >
        Registrar decisão de {title.toLowerCase()}
      </Button>
    </div>
  );
}
