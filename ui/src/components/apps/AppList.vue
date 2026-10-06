<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <KsButton type="primary" data-test="app-create" @click="openEditor()">
                {{ $t("appsPage.create") }}
            </KsButton>
        </template>
    </TopNavBar>
    <section class="full-container">
        <KsTable :data="apps" stripe data-test="apps">
            <KsTableColumn :label="$t('name')">
                <template #default="scope">
                    <router-link :to="{name: 'apps/view', params: {namespace: scope.row.namespace, id: scope.row.id}}">
                        {{ scope.row.displayName || scope.row.id }}
                    </router-link>
                </template>
            </KsTableColumn>
            <KsTableColumn prop="namespace" :label="$t('namespace')" />
            <KsTableColumn prop="flowId" :label="$t('flow')" />
            <KsTableColumn :label="$t('actions')" align="right">
                <template #default="scope">
                    <KsIconButton :aria-label="$t('edit')" :title="$t('edit')" @click="openEditor(scope.row as App)">
                        <Pencil />
                    </KsIconButton>
                    <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove(scope.row as App)">
                        <Delete />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsTable>
    </section>

    <KsDialog v-model="editorVisible" :title="editing ? $t('edit') : $t('appsPage.create')" :dirty="isDirty" destroyOnClose appendToBody width="900px">
        <KsEditor
            v-bind="editorBindings"
            v-model="source"
            lang="yaml"
            :options="{fullHeight: false, lineNumbers: true}"
            :navbar="false"
            data-test="app-editor"
        />
        <template #footer>
            <KsButton @click="editorVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" data-test="app-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useClient} from "@kestra-io/kestra-sdk"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../utils/toast"
    import {APP_TEMPLATE, type App} from "./appTypes"

    const {t} = useI18n()
    const toast = useToast()
    const axios = useClient()
    const editorBindings = useEditorBindings()

    const apps = ref<App[]>([])
    const editorVisible = ref(false)
    const editing = ref<App>()
    const source = ref("")
    const baseline = ref("")
    const isDirty = computed(() => source.value !== baseline.value)

    async function load() {
        const response = await axios.get<{results: App[]}>(`${apiUrl()}/apps`)
        apps.value = response.data.results
    }

    function openEditor(app?: App) {
        editing.value = app
        source.value = app?.source ?? APP_TEMPLATE
        baseline.value = source.value
        editorVisible.value = true
    }

    async function save() {
        const headers = {"Content-Type": "application/x-yaml"}
        const response = editing.value
            ? await axios.put<App>(`${apiUrl()}/apps/${editing.value.namespace}/${editing.value.id}`, source.value, {headers})
            : await axios.post<App>(`${apiUrl()}/apps`, source.value, {headers})
        toast.saved(response.data.displayName || response.data.id)
        editorVisible.value = false
        await load()
    }

    function remove(app: App) {
        toast.confirm(t("delete confirm", {name: app.displayName || app.id}), async () => {
            await axios.delete(`${apiUrl()}/apps/${app.namespace}/${app.id}`)
            toast.deleted(app.displayName || app.id)
            await load()
        })
    }

    const routeInfo = computed(() => ({title: t("apps")}))
    useRouteContext(routeInfo)

    onMounted(load)
</script>
