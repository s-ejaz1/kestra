export type CaseStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "CLOSED"
export type CaseSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"

export const CASE_STATUSES: CaseStatus[] = ["OPEN", "ACKNOWLEDGED", "RESOLVED", "CLOSED"]
export const CASE_SEVERITIES: CaseSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]

export interface LinkedExecution {
    executionId: string;
    namespace: string;
    flowId: string;
    state?: string;
    linked: string;
}

export interface Case {
    id: string;
    title: string;
    description?: string;
    status: CaseStatus;
    severity: CaseSeverity;
    namespace?: string;
    flowId?: string;
    autoLink: boolean;
    assignees: string[];
    labels: string[];
    executions: LinkedExecution[];
    assets: string[];
    dueDate?: string;
    createdBy?: string;
    created: string;
    updated: string;
    acknowledgedDate?: string;
    resolvedDate?: string;
}

export type CaseActivityType =
    | "CREATED"
    | "COMMENT"
    | "STATUS_CHANGED"
    | "SEVERITY_CHANGED"
    | "ASSIGNEES_CHANGED"
    | "DUE_DATE_CHANGED"
    | "UPDATED"
    | "EXECUTION_LINKED"
    | "EXECUTION_UNLINKED"
    | "ASSET_LINKED"
    | "ASSET_UNLINKED"

export interface CaseActivity {
    id: string;
    type: CaseActivityType;
    author: string;
    date: string;
    message?: string;
    from?: string;
    to?: string;
}

export interface Assignee {
    email: string;
    firstName?: string;
    lastName?: string;
}

/** What the case form edits; the status, the links and the timeline change through their own actions. */
export interface CaseDraft {
    title: string;
    description?: string;
    severity: CaseSeverity;
    namespace?: string;
    flowId?: string;
    autoLink: boolean;
    assignees: string[];
    labels: string[];
    /** A string as the API returns it, a Date once picked; both serialize to an ISO instant. */
    dueDate?: string | Date;
    executionIds?: string[];
}

type TagType = "success" | "danger" | "warning" | "info" | "primary"

export const STATUS_TAG: Record<CaseStatus, TagType> = {
    OPEN: "danger",
    ACKNOWLEDGED: "warning",
    RESOLVED: "success",
    CLOSED: "info",
}

export const SEVERITY_TAG: Record<CaseSeverity, TagType> = {
    CRITICAL: "danger",
    HIGH: "warning",
    MEDIUM: "primary",
    LOW: "info",
}

export function isOpen(value: Case) {
    return value.status === "OPEN" || value.status === "ACKNOWLEDGED"
}

export function isSlaBreached(value: Case) {
    return isOpen(value) && !!value.dueDate && new Date(value.dueDate).getTime() < Date.now()
}

export function draftOf(value: Case): CaseDraft {
    return {
        title: value.title,
        description: value.description,
        severity: value.severity,
        namespace: value.namespace,
        flowId: value.flowId,
        autoLink: value.autoLink,
        assignees: [...value.assignees],
        labels: [...value.labels],
        dueDate: value.dueDate,
    }
}
