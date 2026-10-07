<template>
    <KsDialog
        :modelValue="modelValue"
        :title="$t('policiesPage.preview')"
        destroyOnClose
        appendToBody
        width="900px"
        @update:model-value="(value: boolean | undefined) => emit('update:modelValue', !!value)"
    >
        <div class="policy-preview" data-test="policy-preview">
            <KsText type="info">
                {{ $t("policiesPage.previewHint") }}
            </KsText>
            <div class="policy-preview-form">
                <KsInput v-model="flowNamespace" :placeholder="$t('namespace')" />
                <KsInput v-model="flowId" :placeholder="$t('flow')" />
                <KsButton type="primary" :disabled="!flowNamespace || !flowId" :loading="loading" data-test="policy-preview-run" @click="run">
                    {{ $t("policiesPage.previewRun") }}
                </KsButton>
            </div>
            <template v-if="preview">
                <KsAlert v-for="message in preview.blocking" :key="message" type="error" :closable="false" :title="message" />
                <KsAlert v-for="message in preview.audits" :key="message" type="warning" :closable="false" :title="message" />
                <KsAlert v-for="message in preview.changes" :key="message" type="info" :closable="false" :title="message" />
                <KsAlert
                    v-if="!preview.blocking.length && !preview.audits.length && !preview.changes.length"
                    type="success"
                    :closable="false"
                    :title="$t('policiesPage.previewClean')"
                />
                <KsEditor
                    v-bind="editorBindings"
                    :modelValue="preview.source"
                    lang="yaml"
                    readOnly
                    :options="{fullHeight: false, lineNumbers: true, readOnly: true}"
                    :navbar="false"
                />
            </template>
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {useClient} from "@kestra-io/kestra-sdk"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"
    import type {PolicyPreview} from "./policyTypes"

    const props = defineProps<{modelValue: boolean; namespace?: string}>()
    const emit = defineEmits<{"update:modelValue": [value: boolean]}>()

    const axios = useClient()
    const editorBindings = useEditorBindings()

    const flowNamespace = ref(props.namespace ?? "")
    const flowId = ref("")
    const preview = ref<PolicyPreview>()
    const loading = ref(false)

    watch(() => props.modelValue, (visible) => {
        if (visible) preview.value = undefined
    })

    async function run() {
        loading.value = true
        try {
            const path = `${encodeURIComponent(flowNamespace.value)}/${encodeURIComponent(flowId.value)}`
            preview.value = (await axios.get<PolicyPreview>(`${apiUrl()}/policies/preview/${path}`)).data
        } finally {
            loading.value = false
        }
    }
</script>

<style scoped>
    .policy-preview {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
    }

    .policy-preview-form {
        display: grid;
        grid-template-columns: 1fr 1fr auto;
        gap: var(--ks-spacing-3);
    }
</style>
