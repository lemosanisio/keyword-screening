# PLD Workbench

Frontend exploratório para testar a experiência do analista PLD sobre o `pld-customer-analysis`.

Este projeto usa React, TypeScript, Bun, Tailwind e componentes no estilo shadcn/ui. Ele é uma demo de aprendizado arquitetural e produto, não uma implementação pronta para produção.

## Scripts

```bash
mise install bun
mise exec -- bun install
mise exec -- bun run build
mise exec -- bun run dev
```

O backend esperado é o `pld-customer-analysis` em `http://localhost:8082`. Para apontar para outro backend, ajuste `globalThis.PLD_API_BASE_URL` em `index.html` neste protótipo.
