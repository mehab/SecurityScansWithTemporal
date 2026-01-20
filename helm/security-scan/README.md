# Security Scan Helm Chart

This Helm chart deploys the Security Scan application with Temporal workflows on Kubernetes.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.0+
- A Temporal service/cluster accessible from the Kubernetes cluster
- A storage class that supports `ReadWriteMany` (RWX) access mode (e.g., NFS, CephFS, GlusterFS)

## Installation

### Quick Start

```bash
# Install with default values
helm install security-scan ./helm/security-scan

# Install with custom values
helm install security-scan ./helm/security-scan -f my-values.yaml

# Install to a specific namespace
helm install security-scan ./helm/security-scan --namespace security-scan --create-namespace
```

### Custom Values

Create a `my-values.yaml` file to override default values:

```yaml
# Example: my-values.yaml
temporal:
  address: "temporal.example.com:7233"

storage:
  pvc:
    size: 200Gi
    storageClassName: my-nfs-storage-class

workers:
  blackduck:
    replicas: 3

restartService:
  schedule: "*/15 * * * *"  # Run every 15 minutes
```

Then install:

```bash
helm install security-scan ./helm/security-scan -f my-values.yaml
```

## Configuration

### Temporal Service

Configure the Temporal service address:

```yaml
temporal:
  address: "temporal-service.security-scan.svc.cluster.local:7233"
  # Or external address:
  # address: "temporal.example.com:7233"
```

### Storage

#### Using Existing Storage Class

If you have an existing storage class that supports RWX:

```yaml
storage:
  storageClass:
    create: false  # Don't create a new StorageClass
  pvc:
    storageClassName: my-existing-nfs-storage-class
    size: 100Gi
```

#### Creating New Storage Class

If you want to create a new StorageClass:

```yaml
storage:
  storageClass:
    create: true
    name: security-scan-nfs
    provisioner: example.com/nfs
    parameters:
      server: nfs-server.your-cluster.local
      path: /exports/security-scans
  pvc:
    storageClassName: security-scan-nfs
    size: 100Gi
```

**⚠️ CRITICAL**: The storage class must support `ReadWriteMany` (RWX) access mode for parallel execution across pods.

### Workers

#### BlackDuck Workers

```yaml
workers:
  blackduck:
    enabled: true
    replicas: 2
    scanType: "BLACKDUCK_DETECT"
    resources:
      requests:
        cpu: "1000m"
        memory: "2Gi"
      limits:
        cpu: "2000m"
        memory: "4Gi"
```

### Workflow Restart Service

The restart service runs as a CronJob to monitor and restart failed workflows:

```yaml
restartService:
  enabled: true
  schedule: "*/30 * * * *"  # Run every 30 minutes
  env:
    failureType: "ALL"  # Or specific: STORAGE_FAILURE, NETWORK_FAILURE, etc.
    verifyStorage: "true"
    useNewWorkflowId: "true"
```

### Secrets

Configure secrets for Git and BlackDuck credentials:

```yaml
secret:
  enabled: true
  stringData:
    git-token: "your-git-token"
    blackduck-token: "your-blackduck-token"
```

**⚠️ SECURITY**: In production, use external secret management:
- Kubernetes External Secrets Operator
- Sealed Secrets
- Vault
- Cloud provider secret managers (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager)

### ConfigMap

Add non-sensitive configuration:

```yaml
configMap:
  enabled: true
  data:
    config.yaml: |
      key: value
```

## Upgrading

```bash
# Upgrade with new values
helm upgrade security-scan ./helm/security-scan -f my-values.yaml

# Upgrade to a new chart version
helm upgrade security-scan ./helm/security-scan --version 1.1.0
```

## Uninstallation

```bash
# Uninstall the release
helm uninstall security-scan

# Uninstall and delete PVC (data will be lost)
helm uninstall security-scan
kubectl delete pvc security-scan-workspace -n security-scan
```

## Values Reference

| Parameter | Description | Default |
|-----------|-------------|---------|
| `global.namespace` | Namespace for all resources | `security-scan` |
| `global.createNamespace` | Create namespace if it doesn't exist | `true` |
| `temporal.address` | Temporal service address | `temporal-service.security-scan.svc.cluster.local:7233` |
| `storage.storageClass.create` | Create a new StorageClass | `false` |
| `storage.storageClass.name` | StorageClass name | `security-scan-nfs` |
| `storage.pvc.create` | Create PersistentVolumeClaim | `true` |
| `storage.pvc.name` | PVC name | `security-scan-workspace` |
| `storage.pvc.size` | PVC size | `100Gi` |
| `storage.pvc.storageClassName` | Storage class for PVC | `security-scan-nfs` |
| `workers.common.image.repository` | Worker image repository | `security-scan-worker` |
| `workers.common.image.tag` | Worker image tag | `latest` |
| `workers.blackduck.enabled` | Enable BlackDuck workers | `true` |
| `workers.blackduck.replicas` | Number of BlackDuck workers | `2` |
| `restartService.enabled` | Enable restart service CronJob | `true` |
| `restartService.schedule` | CronJob schedule | `*/30 * * * *` |
| `restartService.env.failureType` | Failure types to restart | `ALL` |
| `configMap.enabled` | Create ConfigMap | `true` |
| `secret.enabled` | Create Secret | `true` |

## Troubleshooting

### Check Pod Status

```bash
# List all pods
kubectl get pods -n security-scan

# Check pod logs
kubectl logs -n security-scan deployment/security-scan-worker-blackduck
kubectl logs -n security-scan deployment/security-scan-worker-blackduck

# Check CronJob logs
kubectl logs -n security-scan job/workflow-restart-service-<timestamp>
```

### Check PVC Status

```bash
# Check PVC
kubectl get pvc -n security-scan

# Check PVC details
kubectl describe pvc security-scan-workspace -n security-scan
```

### Check Storage Class

```bash
# List storage classes
kubectl get storageclass

# Check storage class details
kubectl describe storageclass security-scan-nfs
```

### Common Issues

1. **PVC Pending**: Check if storage class exists and supports RWX
2. **Pods Not Starting**: Check Temporal service connectivity
3. **Workers Not Processing**: Check task queue names and Temporal connection
4. **Storage Issues**: Verify RWX access mode is supported

## Additional Resources

- [Kubernetes Deployment Guide](../../README.md#deployment-on-kubernetes)
- [OpenShift Deployment Guide](../../OPENSHIFT_DEPLOYMENT.md)
- [Architecture Documentation](../../ARCHITECTURE_DIAGRAM.md)
