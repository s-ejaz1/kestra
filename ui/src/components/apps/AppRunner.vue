<template>
    <TopNavBar :title="app?.displayName || app?.id || $t('apps')" />
    <section class="full-container app-runner" data-test="app-runner">
        <KsAlert v-if="loadError" type="error" :closable="false">
            {{ loadError }}
        </KsAlert>
        <template v-else-if="app && flow">
            <KsForm labelPosition="top" :model="inputs" ref="form" @submit.prevent="false">
                <template v-for="(block, index) in blocks" :key="`${stage}-${index}`">
                    <KsMarkdown v-if="block.type === 'Markdown'" :content="String(block.content ?? '')" />

                    <KsAlert
                        v-else-if="block.type === 'Alert'"
                        :type="alertType(block.style)"
                        :closable="false"
                        :title="String(block.content ?? '')"
                    />

                    <template v-else-if="block.type === 'CreateExecutionForm'">
                        <InputsForm
                            v-if="flow.inputs?.length"
                            :initialInputs="flow.inputs"
                            :flow="flow"
                            v-model="inputs"
                            @update:model-value-no-default="(values: Record<string, unknown>) => inputsNoDefaults = values"
                            @confirm="submit"
                        />
                    </template>

                    <div v-else-if="block.type === 'CreateExecutionButton'" class="app-actions">
                        <KsButton type="primary" :loading="submitting" data-test="app-submit" @click="submit">
                            {{ block.text ?? $t("execute") }}
                        </KsButton>
                    </div>

                    <div v-else-if="block.type === 'Loading'" v-ksLoading="true" class="app-loading" />

                    <div v-else-if="block.type === 'Logs'" class="app-logs">
                        <LogLine
                            v-for="log in logs"
                            :key="`${log.timestamp}-${log.message}`"
                            :level="log.level"
                            :log="log"
                            :excludeMetas="['namespace', 'flowId', 'executionId', 'taskRunId']"
                        />
                    </div>

                    <KsJsonTree v-else-if="block.type === 'Outputs' && outputs" :value="outputs" defaultExpanded />
                </template>
            </KsForm>
        </template>
    </section>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
    import type {FormInstance} from "@kestra-io/design-system"
    import TopNavBar from "../layout/TopNavBar.vue"
    import InputsForm from "../inputs/InputsForm.vue"
    import LogLine from "../logs/LogLine.vue"
    import {apiUrl} from "override/utils/route"
    import {useExecutionsStore} from "../../stores/executions"
    import type {Execution} from "../../stores/executions"
    import type {Flow} from "../../stores/flow"
    import {normalizeInputValues} from "../../utils/submitTask"
    import {flattenInputs} from "../../utils/inputs"
    import type {App, AppBlock, AppStage} from "./appTypes"

    const SUCCESS_STATES = ["SUCCESS", "WARNING", "SKIPPED"]
    const FAILURE_STATES = ["FAILED", "KILLED", "CANCELLED"]

    const route = useRoute()
    const axios = useClient()
    const executionsStore = useExecutionsStore()

    const app = ref<App>()
    const flow = ref<Flow>()
    const execution = ref<Execution>()
    const logs = ref<Awaited<ReturnType<typeof executionsStore.loadLogs>>>([])
    const loadError = ref<string>()
    const form = ref<FormInstance>()
    const inputs = ref<Record<string, unknown>>({})
    const inputsNoDefaults = ref<Record<string, unknown>>({})
    const submitting = ref(false)
    let subscription: {close: () => void} | undefined

    const stage = computed<AppStage>(() => {
        const state = execution.value?.state?.current
        if (!state) return "OPEN"
        if (SUCCESS_STATES.includes(state)) return "SUCCESS"
        if (FAILURE_STATES.includes(state)) return "FAILURE"
        return "RUNNING"
    })

    // Flow outputs are returned by the API but missing from the store's Execution type.
    const outputs = computed(() => (execution.value as (Execution & {outputs?: Record<string, unknown>}) | undefined)?.outputs)

    const blocks = computed<AppBlock[]>(() => app.value?.layout.find(layout => layout.on === stage.value)?.blocks ?? [])

    function alertType(style: unknown) {
        return ({SUCCESS: "success", WARNING: "warning", ERROR: "error"} as Record<string, "success" | "warning" | "error">)[String(style)] ?? "info"
    }

    async function refreshLogs() {
        if (!execution.value) return
        logs.value = await executionsStore.loadLogs({executionId: execution.value.id, params: {minLevel: "INFO"}, store: false})
    }

    function submit() {
        if (!form.value || !flow.value || submitting.value) return
        form.value.validate(async (valid: boolean) => {
            if (!valid || !flow.value) return
            submitting.value = true
            try {
                execution.value = await executionsStore.triggerExecution({
                    namespace: flow.value.namespace,
                    id: flow.value.id,
                    kind: "NORMAL",
                    formData: normalizeInputValues(flattenInputs(flow.value.inputs), inputsNoDefaults.value),
                })
                subscription = executionsStore.subscribeToExecution(execution.value.id, {
                    onExecution: (updated) => {
                        execution.value = updated
                        void refreshLogs()
                    },
                    onEnd: () => void refreshLogs(),
                })
            } finally {
                submitting.value = false
            }
        })
    }

    onMounted(async () => {
        try {
            const response = await axios.get<App>(`${apiUrl()}/apps/${route.params.namespace}/${route.params.id}`)
            app.value = response.data
            flow.value = await executionsStore.loadFlowForExecution({namespace: app.value.namespace, flowId: app.value.flowId, store: false}) as Flow
        } catch (error) {
            loadError.value = (error as Error).message
        }
    })

    onBeforeUnmount(() => subscription?.close())
</script>

<style scoped>
    .app-runner {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        max-width: 60rem;
        margin: 0 auto;
        padding: var(--ks-spacing-5);
    }

    .app-actions {
        display: flex;
        justify-content: flex-end;
        margin: var(--ks-spacing-3) 0;
    }

    .app-loading {
        min-height: var(--ks-spacing-16);
    }

    .app-logs {
        max-height: 24rem;
        overflow: auto;
    }
</style>
