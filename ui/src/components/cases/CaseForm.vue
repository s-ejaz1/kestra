<template>
    <KsDialog
        :modelValue="modelValue"
        :title="editing ? $t('casesPage.edit') : $t('casesPage.create')"
        :dirty="isDirty"
        destroyOnClose
        appendToBody
        width="720px"
        @update:model-value="(value: boolean | undefined) => emit('update:modelValue', !!value)"
    >
        <KsForm labelPosition="top" :model="form" data-test="case-form" @submit.prevent>
            <KsFormItem :label="$t('casesPage.title')" required>
                <KsInput v-model="form.title" data-test="case-title" />
            </KsFormItem>
            <KsFormItem :label="$t('description')">
                <KsInput v-model="form.description" type="textarea" :rows="4" :placeholder="$t('casesPage.descriptionHint')" />
            </KsFormItem>
            <div class="case-form-row">
                <KsFormItem :label="$t('casesPage.severity')">
                    <KsSelect v-model="form.severity" data-test="case-severity">
                        <KsOption v-for="severity in CASE_SEVERITIES" :key="severity" :label="severity" :value="severity" />
                    </KsSelect>
                </KsFormItem>
                <KsFormItem :label="$t('casesPage.dueDate')">
                    <KsDatePicker v-model="form.dueDate" type="datetime" :placeholder="$t('casesPage.dueDateHint')" />
                </KsFormItem>
            </div>
            <div class="case-form-row">
                <KsFormItem :label="$t('namespace')">
                    <KsInput v-model="form.namespace" />
                </KsFormItem>
                <KsFormItem :label="$t('flow')">
                    <KsInput v-model="form.flowId" />
                </KsFormItem>
            </div>
            <KsFormItem>
                <KsSwitch v-model="form.autoLink" :disabled="!form.flowId" :activeText="$t('casesPage.autoLink')" data-test="case-auto-link" />
            </KsFormItem>
            <KsFormItem :label="$t('casesPage.assignees')">
                <KsSelect v-model="form.assignees" multiple filterable allowCreate defaultFirstOption data-test="case-assignees">
                    <KsOption v-for="assignee in assignees" :key="assignee.email" :label="assignee.email" :value="assignee.email" />
                </KsSelect>
            </KsFormItem>
            <KsFormItem :label="$t('labels')">
                <KsSelect v-model="form.labels" multiple filterable allowCreate defaultFirstOption />
            </KsFormItem>
        </KsForm>
        <template #footer>
            <KsButton @click="emit('update:modelValue', false)">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" :disabled="!form.title.trim()" :loading="saving" data-test="case-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue"
    import {useClient} from "@kestra-io/kestra-sdk"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../utils/toast"
    import {CASE_SEVERITIES, type Assignee, type Case, type CaseDraft} from "./caseTypes"

    const props = defineProps<{
        modelValue: boolean;
        initial?: Partial<CaseDraft>;
        editing?: Case;
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: boolean];
        saved: [value: Case];
    }>()

    const axios = useClient()
    const toast = useToast()

    const empty = (): CaseDraft => ({title: "", severity: "MEDIUM", autoLink: false, assignees: [], labels: []})
    const form = ref<CaseDraft>(empty())
    const baseline = ref("")
    const isDirty = computed(() => JSON.stringify(form.value) !== baseline.value)
    const assignees = ref<Assignee[]>([])
    const saving = ref(false)

    watch(() => props.modelValue, (visible) => {
        if (!visible) return
        form.value = {...empty(), ...props.initial}
        baseline.value = JSON.stringify(form.value)
    }, {immediate: true})

    watch(() => form.value.flowId, (flowId) => {
        if (!flowId) form.value.autoLink = false
    })

    async function save() {
        saving.value = true
        try {
            const response = props.editing
                ? await axios.put<Case>(`${apiUrl()}/cases/${props.editing.id}`, form.value)
                : await axios.post<Case>(`${apiUrl()}/cases`, form.value)
            toast.saved(response.data.title)
            emit("saved", response.data)
            emit("update:modelValue", false)
        } finally {
            saving.value = false
        }
    }

    onMounted(async () => {
        assignees.value = (await axios.get<{results: Assignee[]}>(`${apiUrl()}/cases/assignees`)).data.results
    })
</script>

<style scoped>
    .case-form-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--ks-spacing-4);
    }
</style>
