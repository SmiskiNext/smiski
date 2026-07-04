# PLG Stack — Promtail + Loki + Grafana

## Prerequisites

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

## Install order: Loki → Promtail → Grafana

### 1. Loki

```bash
helm install loki grafana/loki \
    --namespace monitoring \
    --create-namespace \
    -f loki-values.yaml
```

### 2. Promtail

```bash
helm install promtail grafana/promtail \
    --namespace monitoring \
    -f promtail-values.yaml
```

### 3. Grafana

```bash
helm install grafana grafana/grafana \
    --namespace monitoring \
    -f grafana-values.yaml
```

## Access Grafana

```bash
kubectl port-forward svc/grafana 3000:80 -n monitoring
```

Open <http://localhost:3000> — default credentials: `admin` /
`change-me-in-production`

## Query logs

In Grafana → Explore → select Loki datasource:

```logql
{app="user-management"} | json | direction="-->"
```

Filters inbound HTTP request log entries from user-management pods.
