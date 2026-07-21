import { Navigate, Route, Routes } from "react-router-dom";
import { QueuePage } from "@/design/pages/queue-page";
import { CaseWorkspacePage } from "@/design/pages/case-workspace-page";
import { WorkbenchShell } from "@/design/templates/workbench-shell";

export function App() {
  return (
    <Routes>
      <Route element={<WorkbenchShell />}>
        <Route index element={<Navigate to="/queue" replace />} />
        <Route path="/queue" element={<QueuePage />} />
        <Route path="/cases/:caseId" element={<CaseWorkspacePage />} />
      </Route>
    </Routes>
  );
}
