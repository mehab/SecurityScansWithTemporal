# OpenShift Deployment Guide

## Overview

This guide explains how to deploy the security scanning application on OpenShift clusters. OpenShift is Kubernetes-based, so the core architecture is identical to Kubernetes, but there are OpenShift-specific considerations.

## Architecture on OpenShift

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Temporal Service (Separate OpenShift Cluster)               │
│  - Runs Temporal Frontend, History, Matching services        │
│  - Can be on same or different OpenShift cluster            │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ gRPC (via Route/Ingress)
                            │
┌─────────────────────────────────────────────────────────────┐
│  Worker OpenShift Cluster                                    │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Project/Namespace: security-scan                    │  │
│  │                                                       │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │  │
│  │  │ Worker Pod 1 │  │ Worker Pod 2 │  │ Worker 3 │ │  │
│  │  │              │  │              │  │          │ │  │
│  │  │ SecurityScan │  │ SecurityScan │  │ Security │ │  │
│  │  │ Worker       │  │ Worker       │  │ Worker   │ │  │
│  │  └──────┬───────┘  └──────┬───────┘  └────┬─────┘ │  │
│  │         │                 │                 │      │  │
│  │         └─────────────────┼─────────────────┘      │  │
│  │                           │                         │  │
│  │                    ┌──────▼──────┐                 │  │
│  │                    │  PVC (RWX)  │                 │  │
│  │                    │  /workspace  │                 │  │
│  │                    └──────────────┘                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## OpenShift-Specific Configuration

### 1. Storage Configuration

#### Storage Class for RWX

OpenShift provides several storage options. You need **ReadWriteMany (RWX)** access mode for parallel execution:

**Option A: NFS Storage Class**
```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: security-scan-nfs
provisioner: example.com/nfs
parameters:
  server: nfs-server.openshift-cluster.local
  path: /exports/security-scans
allowVolumeExpansion: true
volumeBindingMode: Immediate
```

**Option B: Use OpenShift Built-in Storage**
- Check available storage classes: `oc get storageclass`
- Look for classes that support RWX (e.g., NFS-based)
- If using GlusterFS or CephFS, they typically support RWX

#### PersistentVolumeClaim

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: security-scan-workspace
  namespace: security-scan
spec:
  accessModes:
    - ReadWriteMany  # CRITICAL: Must be RWX
  storageClassName: security-scan-nfs  # Your RWX-capable storage class
  resources:
    requests:
      storage: 100Gi
```

### 2. Security Context Constraints (SCC)

OpenShift uses SCCs to control pod security. Worker pods may need specific permissions:

#### Create Custom SCC (if needed)

```yaml
apiVersion: security.openshift.io/v1
kind: SecurityContextConstraints
metadata:
  name: security-scan-worker
allowHostDirVolumePlugin: false
allowHostIPC: false
allowHostNetwork: false
allowHostPID: false
allowHostPorts: false
allowPrivilegedContainer: false
allowedCapabilities: []
defaultAddCapabilities: []
fsGroup:
  type: MustRunAs
readOnlyRootFilesystem: false
requiredDropCapabilities: []
runAsUser:
  type: MustRunAsRange
  uidRangeMin: 1000
  uidRangeMax: 65535
seLinuxContext:
  type: MustRunAs
supplementalGroups:
  type: RunAsAny
volumes:
  - configMap
  - emptyDir
  - persistentVolumeClaim
  - secret
  - downwardAPI
```

#### Grant SCC to ServiceAccount

```bash
oc adm policy add-scc-to-user security-scan-worker \
  system:serviceaccount:security-scan:security-scan-worker
```

### 3. Service Account and RBAC

#### Service Account

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: security-scan-worker
  namespace: security-scan
```

#### RBAC (if needed for storage operations)

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: security-scan-worker
  namespace: security-scan
rules:
- apiGroups: [""]
  resources: ["persistentvolumeclaims"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: security-scan-worker
  namespace: security-scan
subjects:
- kind: ServiceAccount
  name: security-scan-worker
  namespace: security-scan
roleRef:
  kind: Role
  name: security-scan-worker
  apiGroup: rbac.authorization.k8s.io
```

### 4. Deployment Configuration

#### Deployment with OpenShift Considerations

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker
  namespace: security-scan
  labels:
    app: security-scan-worker
spec:
  replicas: 3
  selector:
    matchLabels:
      app: security-scan-worker
  template:
    metadata:
      labels:
        app: security-scan-worker
    spec:
      serviceAccountName: security-scan-worker  # Use dedicated SA
      containers:
      - name: worker
        image: security-scan-worker:latest
        imagePullPolicy: Always
        env:
        - name: TEMPORAL_ADDRESS
          value: "temporal-service.temporal-namespace.svc.cluster.local:7233"
        - name: WORKSPACE_BASE_DIR
          value: "/workspace/security-scans"
        resources:
          requests:
            cpu: "1000m"
            memory: "2Gi"
          limits:
            cpu: "2000m"
            memory: "4Gi"
        volumeMounts:
        - name: workspace-storage
          mountPath: /workspace
        securityContext:
          runAsNonRoot: true  # OpenShift default
          runAsUser: 1000
          fsGroup: 1000
        livenessProbe:
          exec:
            command:
            - pgrep
            - -f
            - security-scan.jar
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          exec:
            command:
            - pgrep
            - -f
            - security-scan.jar
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: workspace-storage
        persistentVolumeClaim:
          claimName: security-scan-workspace
```

### 5. Network Configuration

#### Temporal Service Connection

If Temporal Service is on a different OpenShift cluster:

**Option A: Use OpenShift Route**
```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: temporal-service
  namespace: temporal
spec:
  to:
    kind: Service
    name: temporal-frontend
  port:
    targetPort: 7233
```

Then configure worker with Route URL:
```yaml
env:
- name: TEMPORAL_ADDRESS
  value: "temporal-service-temporal.apps.openshift-cluster.com:443"
```

**Option B: Use Service Mesh (if available)**
- Configure Istio/Service Mesh for cross-cluster communication
- Use service mesh policies for secure communication

#### Network Policies (Optional)

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: security-scan-worker
  namespace: security-scan
spec:
  podSelector:
    matchLabels:
      app: security-scan-worker
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: temporal
    ports:
    - protocol: TCP
      port: 7233
  - to:
    - namespaceSelector: {}  # Allow access to all namespaces for PVC
```

### 6. Resource Quotas

Set appropriate quotas for the namespace:

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: security-scan-quota
  namespace: security-scan
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    limits.cpu: "20"
    limits.memory: 40Gi
    persistentvolumeclaims: "10"
    requests.storage: 500Gi
```

### 7. ConfigMaps and Secrets

#### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: security-scan-config
  namespace: security-scan
data:
  # Add any other configuration here
```

#### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: security-scan-secrets
  namespace: security-scan
type: Opaque
stringData:
  git-token: "your-git-token"
  blackduck-token: "your-blackduck-token"
```

## Deployment Steps

### 1. Create Project/Namespace

```bash
oc new-project security-scan
```

### 2. Create Storage Resources

```bash
oc apply -f storage-class.yaml
oc apply -f pvc.yaml
```

### 3. Create Service Account and RBAC

```bash
oc apply -f service-account.yaml
oc apply -f rbac.yaml
oc adm policy add-scc-to-user security-scan-worker \
  system:serviceaccount:security-scan:security-scan-worker
```

### 4. Create ConfigMap and Secrets

```bash
oc create configmap security-scan-config \
  --from-literal=example-key=example-value

oc create secret generic security-scan-secrets \
  --from-literal=git-token=your-token
```

### 5. Deploy Worker

```bash
oc apply -f kubernetes-deployment.yaml
```

### 6. Deploy Workflow Restart Service (CronJob)

The workflow restart service runs every 30 minutes to automatically restart workflows that failed due to storage failures:

```bash
oc apply -f kubernetes-cronjob-restart-service.yaml
```

Verify the CronJob is created:

```bash
oc get cronjob -n security-scan
oc describe cronjob workflow-restart-service -n security-scan
```

The CronJob will:
- Run every 30 minutes (0.5 hours)
- Check for workflows that failed due to storage issues
- Verify storage health before restarting
- Restart failed workflows with their original scan requests

### 7. Verify Deployment

```bash
# Check pods
oc get pods -n security-scan

# Check PVC
oc get pvc -n security-scan

# Check logs
oc logs -f deployment/security-scan-worker -n security-scan

# Check CronJob and its jobs
oc get cronjob -n security-scan
oc get jobs -n security-scan
oc get pods -n security-scan | grep workflow-restart

# View logs from the latest restart service job
oc logs -l app=workflow-restart-service -n security-scan --tail=100
```

## Monitoring and Troubleshooting

### Common Issues

1. **PVC Not Mounting**
   - Check storage class supports RWX
   - Verify SCC allows PVC volumes
   - Check pod security context

2. **Permission Denied**
   - Verify SCC configuration
   - Check runAsUser matches PVC ownership
   - Ensure fsGroup is set correctly

3. **Cannot Connect to Temporal**
   - Verify network policies allow egress
   - Check Route/Ingress configuration
   - Verify service discovery (DNS)

4. **Out of Space**
   - Monitor PVC usage: `oc describe pvc security-scan-workspace`
   - Check resource quotas
   - Review cleanup logic

### Monitoring

```bash
# Watch pod status
oc get pods -w -n security-scan

# Check resource usage
oc top pods -n security-scan

# View events
oc get events -n security-scan --sort-by='.lastTimestamp'
```

## Scaling

### Horizontal Pod Autoscaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: security-scan-worker-hpa
  namespace: security-scan
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: security-scan-worker
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## Summary

OpenShift deployment is very similar to Kubernetes, with these key considerations:

1. **Storage**: Ensure RWX-capable storage class
2. **Security**: Configure SCCs appropriately
3. **Service Accounts**: Use dedicated SA with proper permissions
4. **Network**: Configure Routes/Ingress for Temporal connection
5. **Resources**: Set appropriate quotas and limits
6. **Monitoring**: Use OpenShift monitoring tools

The core application logic remains the same - only the deployment configuration differs.

