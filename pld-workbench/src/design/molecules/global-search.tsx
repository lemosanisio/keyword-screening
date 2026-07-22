import * as React from "react";
import { useNavigate } from "react-router-dom";
import { requestJson } from "@/api/http";
import { Search } from "lucide-react";

type SearchResult = {
  parties: Array<{ partyId: string; partyType: string; officialName: string; matchReason: string }>;
  cases: Array<{ caseId: string; partyId: string; status: string; priority: string; reasonCode: string; matchReason: string }>;
};

export function GlobalSearch() {
  const [query, setQuery] = React.useState("");
  const [results, setResults] = React.useState<SearchResult | null>(null);
  const [open, setOpen] = React.useState(false);
  const navigate = useNavigate();
  const ref = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    if (query.length < 2) { setResults(null); return; }
    const timeout = setTimeout(async () => {
      try {
        const data = await requestJson<SearchResult>(`/v1/search?q=${encodeURIComponent(query)}`);
        setResults(data);
        setOpen(true);
      } catch { setResults(null); }
    }, 300);
    return () => clearTimeout(timeout);
  }, [query]);

  React.useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const hasResults = results && (results.parties.length > 0 || results.cases.length > 0);

  return (
    <div ref={ref} className="relative">
      <div className="flex items-center gap-1.5 rounded-md border bg-muted/50 px-2 py-1">
        <Search className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          type="text"
          placeholder="Buscar party, caso..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => { if (hasResults) setOpen(true); }}
          className="w-40 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
        />
      </div>
      {open && hasResults && (
        <div className="absolute right-0 top-full z-50 mt-1 w-80 rounded-md border bg-background shadow-lg">
          {results!.parties.length > 0 && (
            <div className="border-b p-2">
              <div className="px-1 text-[10px] font-medium uppercase text-muted-foreground">Parties</div>
              {results!.parties.map((p) => (
                <button
                  key={p.partyId}
                  className="mt-1 w-full rounded px-2 py-1 text-left text-sm hover:bg-muted"
                  onClick={() => { setOpen(false); setQuery(""); navigate(`/queue`); }}
                >
                  <div className="font-medium">{p.officialName}</div>
                  <div className="text-xs text-muted-foreground">{p.partyId} · {p.matchReason}</div>
                </button>
              ))}
            </div>
          )}
          {results!.cases.length > 0 && (
            <div className="p-2">
              <div className="px-1 text-[10px] font-medium uppercase text-muted-foreground">Casos</div>
              {results!.cases.map((c) => (
                <button
                  key={c.caseId}
                  className="mt-1 w-full rounded px-2 py-1 text-left text-sm hover:bg-muted"
                  onClick={() => { setOpen(false); setQuery(""); navigate(`/cases/${c.caseId}`); }}
                >
                  <div className="font-medium">{c.caseId}</div>
                  <div className="text-xs text-muted-foreground">{c.status} · {c.priority} · {c.matchReason}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
