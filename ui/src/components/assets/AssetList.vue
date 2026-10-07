<template>
    <TopNavBar v-if="!namespace" :title="routeInfo.title">
        <template #actions>
            <KsButton type="primary" data-test="asset-create" @click="openEditor">
                {{ $t("assetsPage.create") }}
            </KsButton>
        </template>
    </TopNavBar>
    <section :class="{'full-container': !namespace}">
        <div class="asset-filters">
            <KsInput
                :modelValue="String(route.query.q ?? '')"
                :placeholder="$t('assetsPage.search')"
                clearable
                data-test="asset-filter-query"
                @change="(value: string | number) => applyFilter('q', String(value))"
            />
            <KsInput
                v-if="!namespace"
                :modelValue="String(route.query.namespace ?? '')"
                :placeholder="$t('namespace')"
                clearable
                data-test="asset-filter-namespace"
                @change="(value: string | number) => applyFilter('namespace', String(value))"
            />
        </div>
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="assets"
            :total="total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :selectable="false"
            data-test="assets"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
        >
            <KsTableColumn :label="$t('id')">
                <template #default="scope">
                    <router-link :to="{name: 'assets/update', params: {assetId: (scope.row as Asset).id}}">
                        {{ (scope.row as Asset).id }}
                    </router-link>
                </template>
            </KsTableColumn>
            <KsTableColumn prop="displayName" :label="$t('assetsPage.displayName')" />
            <KsTableColumn :label="$t('type')">
                <template #default="scope">
                    {{ shortType((scope.row as Asset).type) }}
                </template>
            </KsTableColumn>
            <KsTableColumn prop="namespace" :label="$t('namespace')" />
            <KsTableColumn :label="$t('assetsPage.updated')">
                <template #default="scope">
                    <KsDateAgo v-if="(scope.row as Asset).updated" :inverted="true" :date="(scope.row as Asset).updated" />
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('actions')" align="right">
                <template #default="scope">
                    <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove(scope.row as Asset)">
                        <Delete />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsDataTable>
    </section>

    <KsDialog v-model="editorVisible" :title="$t('assetsPage.create')" :dirty="isDirty" destroyOnClose appendToBody width="900px">
        <KsEditor
            v-bind="editorBindings"
            v-model="source"
            lang="yaml"
            :options="{fullHeight: false, lineNumbers: true}"
            :navbar="false"
            data-test="asset-editor"
        />
        <template #footer>
            <KsButton @click="editorVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" data-test="asset-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
    import Delete from "vue-material-design-icons/Delete.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../utils/toast"
    import {ASSET_TEMPLATE, shortType, type Asset} from "./assetTypes"

    const props = defineProps<{namespace?: string}>()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const axios = useClient()
    const toast = useToast()
    const editorBindings = useEditorBindings()
    const dataTable = useTemplateRef("dataTable")

    const assets = ref<Asset[]>([])
    const total = ref(0)
    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)
    const editorVisible = ref(false)
    const source = ref("")
    const isDirty = computed(() => source.value !== ASSET_TEMPLATE)

    async function loadData({page, size}: {page: number; size: number}) {
        const params = new URLSearchParams({page: String(page), size: String(size)})
        const namespace = props.namespace ?? route.query.namespace
        if (namespace) params.set("namespace", String(namespace))
        if (route.query.q) params.set("q", String(route.query.q))
        const response = await axios.get<{results: Asset[]; total: number}>(`${apiUrl()}/assets/search?${params}`)
        assets.value = response.data.results
        total.value = response.data.total
    }

    function applyFilter(key: string, value: string) {
        const {[key]: _previous, page: _page, ...rest} = route.query
        router.push({query: value ? {...rest, [key]: value} : rest})
    }

    function openEditor() {
        source.value = ASSET_TEMPLATE
        editorVisible.value = true
    }

    async function save() {
        const response = await axios.post<Asset>(`${apiUrl()}/assets`, source.value, {headers: {"Content-Type": "application/x-yaml"}})
        toast.saved(response.data.displayName || response.data.id)
        editorVisible.value = false
        dataTable.value?.resetAndReload()
    }

    function remove(asset: Asset) {
        toast.confirm(t("delete confirm", {name: asset.id}), async () => {
            await axios.delete(`${apiUrl()}/assets/${encodeURIComponent(asset.id)}`)
            toast.deleted(asset.id)
            dataTable.value?.resetAndReload()
        })
    }

    const filterKey = computed(() => JSON.stringify([route.query.namespace, route.query.q]))
    watch(filterKey, () => dataTable.value?.resetAndReload())

    const routeInfo = computed(() => ({title: t("assets.title")}))
    if (!props.namespace) {
        useRouteContext(routeInfo)
    }
</script>

<style scoped>
    .asset-filters {
        display: flex;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-3);
        max-width: 40rem;
    }
</style>
