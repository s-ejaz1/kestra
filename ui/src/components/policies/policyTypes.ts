export type PolicyEnforcement = "ENFORCE" | "AUDIT" | "REFERENCE" | "DISABLED"

export interface PolicyRule {
    type: string;
}

export interface Policy {
    id: string;
    namespace?: string;
    description?: string;
    enforcement: PolicyEnforcement;
    rules: PolicyRule[];
    source: string;
    updated?: string;
}

export interface PolicyPreview {
    source: string;
    changes: string[];
    blocking: string[];
    audits: string[];
}

export const ENFORCEMENT_TAG: Record<PolicyEnforcement, "danger" | "warning" | "primary" | "info"> = {
    ENFORCE: "danger",
    AUDIT: "warning",
    REFERENCE: "primary",
    DISABLED: "info",
}

export const POLICY_TEMPLATE = `id: company-governance
# Leave the namespace out to govern the whole tenant; a namespace covers its children too.
namespace: company
description: Shared defaults and guardrails for every flow of the company.
# ENFORCE blocks violating flows, AUDIT only reports them (staged rollout),
# REFERENCE applies to the flows and tasks listing this id in their policyRefs, DISABLED turns it off.
enforcement: AUDIT
rules:
  - type: Defaults
    target: io.kestra.plugin.core.http.*
    values:
      timeout: PT1M
  - type: Labels
    override: true
    labels:
      owner: platform
  - type: BlockPlugins
    plugins:
      - io.kestra.plugin.scripts.shell.*
  - type: PropertyBounds
    target: io.kestra.plugin.core.flow.Sleep
    property: duration
    allowedValues:
      - PT1S
      - PT5S
  - type: RequireLabels
    keys:
      - team
`
