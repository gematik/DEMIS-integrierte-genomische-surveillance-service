{{/*
Expand the name of the chart.
*/}}
{{- define "integrierte-genomische-surveillance-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "integrierte-genomische-surveillance-service.fullname" -}}
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

{{- define "integrierte-genomische-surveillance-service.fullversionname" -}}
{{- if .Values.istio.enable }}
{{- $name := include "integrierte-genomische-surveillance-service.fullname" . }}
{{- $version := regexReplaceAll "\\.+" .Chart.Version "-" }}
{{- printf "%s-%s" $name $version | trunc 63 }}
{{- else }}
{{- include "integrierte-genomische-surveillance-service.fullname" . }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "integrierte-genomische-surveillance-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "integrierte-genomische-surveillance-service.labels" -}}
helm.sh/chart: {{ include "integrierte-genomische-surveillance-service.chart" . }}
{{ include "integrierte-genomische-surveillance-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- if .Values.istio.enable }}
version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.customLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Deployment labels
*/}}
{{- define "integrierte-genomische-surveillance-service.deploymentLabels" -}}
istio-validate-jwt: "{{ .Values.istio.validateJwt | required ".Values.istio.validateJwt is required" }}"
{{- with .Values.deploymentLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "integrierte-genomische-surveillance-service.selectorLabels" -}}
{{- if .Values.istio.enable }}
app: {{ include "integrierte-genomische-surveillance-service.name" . }}
version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/name: {{ include "integrierte-genomische-surveillance-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "integrierte-genomische-surveillance-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "integrierte-genomische-surveillance-service.fullversionname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Effective secrets to mount.
Combines the static secrets (.Values.secret) with the S3 credential secret that
is selected by the object-storage-service feature flag (.Values.featureFlags.useObjectStorageServiceSecret):
  true  -> .Values.s3Credentials.objectStorageService (object-storage-service-secret)
  false -> .Values.s3Credentials.minio                (minio-secret, default)
*/}}
{{- define "integrierte-genomische-surveillance-service.secrets" -}}
{{- $secrets := deepCopy (.Values.secret | default dict) -}}
{{- if .Values.featureFlags.useObjectStorageServiceSecret -}}
{{- $secrets = merge $secrets (.Values.s3Credentials.objectStorageService | default dict) -}}
{{- else -}}
{{- $secrets = merge $secrets (.Values.s3Credentials.minio | default dict) -}}
{{- end -}}
{{- $secrets | toYaml -}}
{{- end -}}

{{/*
Environment Variables
*/}}
{{- define "integrierte-genomische-surveillance-service.env" -}}
{{- $envs := dict -}}
{{- $secrets := include "integrierte-genomische-surveillance-service.secrets" . | fromYaml -}}
{{- if $secrets -}}
{{- $envs = set $envs "SPRING_CONFIG_IMPORT" "configtree:/secrets/*/" }}
{{- end -}}
{{- $envs = set $envs "FEATURE_FLAG_USE_OBJECT_STORAGE_SERVICE_SECRET" (.Values.featureFlags.useObjectStorageServiceSecret | default false) -}}
{{- if .Values.customEnvVars -}}
{{- range $key, $value := .Values.customEnvVars -}}
{{- if not (kindIs "invalid" $value) -}}
{{- $envs = set $envs $key $value }}
{{- end -}}
{{- end -}}
{{- end -}}
{{- if .Values.debug.enable -}}
{{- $toolOptions := printf "%s %s" (get $envs "JAVA_TOOL_OPTIONS") .Values.debug.params | trim -}}
{{- $envs = set $envs "JAVA_TOOL_OPTIONS" $toolOptions -}}
{{- end -}}
{{- range $i, $key := keys $envs | sortAlpha -}}
{{- if $i }}
{{ end -}}
{{- $v := get $envs $key -}}
- name: {{ $key | quote }}
{{- if kindIs "string" $v }}
  value: {{ tpl $v $ | quote }}
{{- else }}
  value: {{ $v | quote }}
{{- end }}
{{- end -}}
{{- end -}}
