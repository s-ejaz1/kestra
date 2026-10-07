<template>
    <TopNavBar v-if="!namespace" :title="routeInfo.title">
        <template #actions>
            <KsButton data-test="policy-preview-open" @click="previewVisible = true">
                {{ $t("policiesPage.preview") }}
            </KsButton>
            <KsButton type="primary" data-test="policy-create" @click="openEditor()">
                {{ $t("policiesPage.create") }}
            </KsButton>
        </template>
    </TopNavBar>
    <section :class="{'full-container': !namespace}">
        <div v-if="namespace" class="policy-actions">
            <KsButton @click="previewVisible = true">
                {{ $t("policiesPage.preview") }}
            </KsButton>
            <KsButton type="primary" @click="openEditor()">
                {{ $t("policiesPage.create") }}
            </KsButton>
        </div>
        <KsAlert v-if="loadError" type="error" :closable="false" :title="loadError" />
        <KsTable v-else :data="policies" stripe data-test="policies">
            <KsTableColumn :label="$t('id')">
                <template #default="scope">
                    {{ (scope.row as Policy).id }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('policiesPage.scope')">
                <template #default="scope">
                    {{ (scope.row as Policy).namespace ?? $t("policiesPage.tenant") }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('policiesPage.enforcement')">
                <template #default="scope">
                    <KsTag :type="ENFORCEMENT_TAG[(scope.row as Policy).enforcement]">
                        {{ (scope.row as Policy).enforcement }}
                    </KsTag>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('policiesPage.rules')">
                <template #default="scope">
                    {{ (scope.row as Policy).rules.map(rule => rule.type).join(", ") }}
                </template>
            </KsTableColumn>
            <KsTableColumn prop="description" :label="$t('description')" />
            <KsTableColumn :label="$t('actions')" align="right">
                <template #default="scope">
                    <KsIconButton :aria-label="$t('edit')" :title="$t('edit')" @click="openEditor(scope.row as Policy)">
                        <Pencil />
                    </KsIconButton>
                    <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove(scope.row as Policy)">
                        <Delete />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsTable>
    </section>

    <KsDialog v-model="editorVisible" :title="editing ? $t('edit') : $t('policiesPage.create')" :dirty="isDirty" destroyOnClose appendToBody width="900px">
        <KsEditor
            v-bind="editorBindings"
            v-model="source"
            lang="yaml"
            :options="{fullHeight: false, lineNumbers: true}"
            :navbar="false"
            data-test="policy-editor"
        />
        <template #footer>
            <KsButton @click="editorVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" data-test="policy-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>

    <PolicyPreviewDialog v-model="previewVisible" :namespace="namespace" />
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useClient} from "@kestra-io/kestra-sdk"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import PolicyPreviewDialog from "./PolicyPreviewDialog.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../utils/toast"
    import {ENFORCEMENT_TAG, POLICY_TEMPLATE, type Policy} from "./policyTypes"

    const props = defineProps<{namespace?: string}>()

    const {t} = useI18n()
    const toast = useToast()
    const axios = useClient()
    const editorBindings = useEditorBindings()

    const policies = ref<Policy[]>([])
    const loadError = ref<string>()
    const editorVisible = ref(false)
    const previewVisible = ref(false)
    const editing = ref<Policy>()
    const source = ref("")
    const baseline = ref("")
    const isDirty = computed(() => source.value !== baseline.value)

    async function load() {
        try {
            const query = props.namespace ? `?namespace=${encodeURIComponent(props.namespace)}` : ""
            policies.value = (await axios.get<{results: Policy[]}>(`${apiUrl()}/policies${query}`)).data.results
        } catch (error) {
            loadError.value = (error as Error).message
        }
    }

    function openEditor(policy?: Policy) {
        editing.value = policy
        source.value = policy?.source ?? POLICY_TEMPLATE
        baseline.value = source.value
        editorVisible.value = true
    }

    async function save() {
        const headers = {"Content-Type": "application/x-yaml"}
        const response = editing.value
            ? await axios.put<Policy>(`${apiUrl()}/policies/${encodeURIComponent(editing.value.id)}`, source.value, {headers})
            : await axios.post<Policy>(`${apiUrl()}/policies`, source.value, {headers})
        toast.saved(response.data.id)
        editorVisible.value = false
        await load()
    }

    function remove(policy: Policy) {
        toast.confirm(t("delete confirm", {name: policy.id}), async () => {
            await axios.delete(`${apiUrl()}/policies/${encodeURIComponent(policy.id)}`)
            toast.deleted(policy.id)
            await load()
        })
    }

    const routeInfo = computed(() => ({title: t("demos.policies.label")}))
    if (!props.namespace) {
        useRouteContext(routeInfo)
    }

    onMounted(load)
</script>

<style scoped>
    .policy-actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-3);
    }
</style>
