#!/usr/bin/env bash
set -euo pipefail

# Install Strimzi Kafka Operator via Helm
# Requires: helm, kubectl with access to k8s cluster

NAMESPACE="kafka"

echo "==> Installing Strimzi Kafka Operator into namespace: ${NAMESPACE}"

helm repo add strimzi https://strimzi.io/charts/ 2>/dev/null || true
helm repo update strimzi

helm upgrade --install strimzi-kafka-operator strimzi/strimzi-kafka-operator \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    --set watchNamespaces="{${NAMESPACE}}" \
    --wait --timeout 5m

echo "==> Strimzi Kafka Operator installed. Apply kafka-cluster.yaml and kafka-topics.yaml next."
