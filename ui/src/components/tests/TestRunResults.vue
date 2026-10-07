<template>
    <div class="test-run-results" data-test="test-run-results">
        <KsCard v-for="result in run.results" :key="result.testId" shadow="never">
            <template #header>
                <div class="test-case-header">
                    <KsTag :type="TEST_STATE_TAG[result.state]">
                        {{ result.state }}
                    </KsTag>
                    <KsText tag="strong">
                        {{ result.testId }}
                    </KsText>
                    <router-link
                        v-if="result.executionId"
                        :to="{name: 'executions/update', params: {namespace: run.namespace, flowId: run.flowId, id: result.executionId}}"
                    >
                        {{ $t("execution") }}
                    </router-link>
                </div>
            </template>

            <KsAlert
                v-for="(error, index) in result.errors"
                :key="index"
                type="error"
                :closable="false"
                :title="error.message"
            />

            <KsTable v-if="result.assertionResults.length" :data="result.assertionResults" size="small">
                <KsTableColumn width="48">
                    <template #default="scope">
                        <KsIcon :name="(scope.row as AssertionResult).isSuccess ? 'check-circle' : 'close-circle'" />
                    </template>
                </KsTableColumn>
                <KsTableColumn :label="$t('testsPage.assertion')">
                    <template #default="scope">
                        {{ (scope.row as AssertionResult).description ?? (scope.row as AssertionResult).operator }}
                    </template>
                </KsTableColumn>
                <KsTableColumn :label="$t('testsPage.expected')">
                    <template #default="scope">
                        <code>{{ format((scope.row as AssertionResult).expected) }}</code>
                    </template>
                </KsTableColumn>
                <KsTableColumn :label="$t('testsPage.actual')">
                    <template #default="scope">
                        <code>{{ format((scope.row as AssertionResult).actual) }}</code>
                    </template>
                </KsTableColumn>
            </KsTable>
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import {TEST_STATE_TAG, type AssertionResult, type TestSuiteRunResult} from "./testTypes"

    defineProps<{run: TestSuiteRunResult}>()

    function format(value: unknown) {
        return typeof value === "string" ? value : JSON.stringify(value)
    }
</script>

<style scoped>
    .test-run-results {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .test-case-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }
</style>
