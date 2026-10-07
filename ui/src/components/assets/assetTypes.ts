import {stringUtils} from "@kestra-io/design-system"
import {ASSET, EDGE, NODE, type Element} from "../dependencies/utils/types"

export interface Asset {
    id: string;
    namespace?: string;
    type: string;
    displayName?: string;
    description?: string;
    metadata?: Record<string, unknown>;
    status?: string;
    owner?: string;
    created?: string;
    updated?: string;
}

export type AssetDirection = "INPUT" | "OUTPUT"

export interface AssetUsage {
    assetId: string;
    direction: AssetDirection;
    namespace: string;
    flowId: string;
    executionId: string;
    taskId: string;
    taskRunId: string;
    state?: string;
    date: string;
}

export interface AssetLineage {
    assets: Asset[];
    edges: {source: string; target: string; namespace?: string; flowId?: string; taskId?: string}[];
}

export const ASSET_TEMPLATE = `id: orders_table
namespace: company.team
type: io.kestra.plugin.ee.assets.Table
displayName: Orders
description: Every order placed on the store, one row per order.
metadata:
  system: postgres
  owner: data-team
`

export function shortType(type?: string) {
    return type ? stringUtils.afterLastDot(type) : ""
}

export function lineageElements(lineage: AssetLineage): Element[] {
    return [
        ...lineage.assets.map((asset): Element => ({
            data: {
                id: asset.id,
                type: NODE,
                flow: asset.id,
                namespace: asset.namespace,
                metadata: {subtype: ASSET, assetType: asset.type, updated: asset.updated, status: asset.status},
            },
        })),
        ...lineage.edges.map((edge): Element => ({
            data: {id: `${edge.source}->${edge.target}`, type: EDGE, source: edge.source, target: edge.target},
        })),
    ]
}
