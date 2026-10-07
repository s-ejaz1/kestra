<template>
    <section class="full-container" data-test="execution-assets">
        <KsTable :data="rows">
            <KsTableColumn prop="taskId" :label="$t('task')" />
            <KsTableColumn :label="$t('assetsPage.direction')">
                <template #default="scope">
                    <KsTag :type="(scope.row as Row).direction === 'OUTPUT' ? 'success' : 'info'">
                        {{ (scope.row as Row).direction }}
                    </KsTag>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('id')">
                <template #default="scope">
                    <router-link :to="{name: 'assets/update', params: {assetId: (scope.row as Row).id}}">
                        {{ (scope.row as Row).id }}
                    </router-link>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('type')">
                <template #default="scope">
                    {{ shortType((scope.row as Row).type) }}
                </template>
            </KsTableColumn>
        </KsTable>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useExecutionsStore} from "../../stores/executions"
    import {shortType, type AssetDirection} from "./assetTypes"

    interface Row {
        taskId: string;
        direction: AssetDirection;
        id: string;
        type?: string;
    }

    interface AssetRef {
        id: string;
        type?: string;
    }

    interface TaskRunWithAssets {
        taskId: string;
        assetEmits?: {inputs?: AssetRef[]; outputs?: AssetRef[]}[];
    }

    const executionsStore = useExecutionsStore()

    const rows = computed<Row[]>(() => {
        const taskRuns = (executionsStore.execution?.taskRunList ?? []) as TaskRunWithAssets[]
        return taskRuns.flatMap(taskRun => (taskRun.assetEmits ?? []).flatMap(bundle => [
            ...(bundle.inputs ?? []).map(asset => ({taskId: taskRun.taskId, direction: "INPUT" as const, id: asset.id, type: asset.type})),
            ...(bundle.outputs ?? []).map(asset => ({taskId: taskRun.taskId, direction: "OUTPUT" as const, id: asset.id, type: asset.type})),
        ]))
    })
</script>
