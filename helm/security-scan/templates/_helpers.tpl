{{/*
Expand the name of the chart.
*/}}
{{- define "security-scan.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "security-scan.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "security-scan.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "security-scan.labels" -}}
helm.sh/chart: {{ include "security-scan.chart" . }}
{{ include "security-scan.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.labels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "security-scan.selectorLabels" -}}
app.kubernetes.io/name: {{ include "security-scan.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "security-scan.workerSelectorLabels" -}}
{{ include "security-scan.selectorLabels" . }}
app: security-scan-worker
{{- end }}

{{/*
Create the namespace
*/}}
{{- define "security-scan.namespace" -}}
{{- default .Values.global.namespace .Values.namespace }}
{{- end }}

{{/*
Create the Temporal address
*/}}
{{- define "security-scan.temporalAddress" -}}
{{- default "temporal-service.security-scan.svc.cluster.local:7233" .Values.temporal.address }}
{{- end }}

{{/*
Create the workspace base directory
*/}}
{{- define "security-scan.workspaceBaseDir" -}}
{{- default "/workspace/security-scans" .Values.workers.common.workspaceBaseDir }}
{{- end }}
