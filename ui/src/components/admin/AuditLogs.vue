<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="full-container">
        <div class="audit-filters">
            <KsInput
                :modelValue="String(route.query.namespace ?? '')"
                :placeholder="$t('namespace')"
                clearable
                data-test="audit-filter-namespace"
                @change="(value: string | number) => applyFilter('namespace', String(value))"
            />
            <KsInput
                :modelValue="String(route.query.user ?? '')"
                :placeholder="$t('email')"
                clearable
                data-test="audit-filter-user"
                @change="(value: string | number) => applyFilter('user', String(value))"
            />
        </div>
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="logs"
            :total="total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :selectable="false"
            fitHeight
            data-test="audit-logs"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
        >
            <KsTableColumn prop="date" :label="$t('date')">
                <template #default="scope">
                    <KsDateAgo :inverted="true" :date="scope.row.date" />
                </template>
            </KsTableColumn>
            <KsTableColumn prop="userEmail" :label="$t('email')" />
            <KsTableColumn prop="method" :label="$t('auditLogs.method')" />
            <KsTableColumn prop="resource" :label="$t('auditLogs.resource')" />
            <KsTableColumn prop="namespace" :label="$t('namespace')" />
            <KsTableColumn prop="path" :label="$t('auditLogs.path')" />
        </KsDataTable>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
    import TopNavBar from "../layout/TopNavBar.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {apiUrl} from "override/utils/route"

    interface AuditLog {
        id: string;
        date: string;
        userEmail: string;
        method: string;
        path: string;
        resource?: string;
        namespace?: string;
    }

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const axios = useClient()
    const dataTable = useTemplateRef("dataTable")

    const logs = ref<AuditLog[]>([])
    const total = ref(0)
    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    async function loadData({page, size}: {page: number; size: number}) {
        const params = new URLSearchParams({page: String(page), size: String(size)})
        for (const key of ["namespace", "user"]) {
            const value = route.query[key]
            if (value) params.set(key, String(value))
        }
        const response = await axios.get<{results: AuditLog[]; total: number}>(`${apiUrl()}/audit-logs/search?${params}`)
        logs.value = response.data.results
        total.value = response.data.total
    }

    function applyFilter(key: string, value: string) {
        const {[key]: _previous, page: _page, ...rest} = route.query
        router.push({query: value ? {...rest, [key]: value} : rest})
    }

    const filterKey = computed(() => JSON.stringify([route.query.namespace, route.query.user]))
    watch(filterKey, () => dataTable.value?.resetAndReload())

    const routeInfo = computed(() => ({title: t("auditlogs")}))
    useRouteContext(routeInfo)
</script>

<style scoped>
    .audit-filters {
        display: flex;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-3);
        max-width: 40rem;
    }
</style>
