<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <KsButton type="primary" data-test="case-create" @click="openForm()">
                {{ $t("casesPage.create") }}
            </KsButton>
        </template>
    </TopNavBar>
    <section class="full-container">
        <div class="case-filters">
            <KsInput
                :modelValue="String(route.query.q ?? '')"
                :placeholder="$t('casesPage.search')"
                clearable
                data-test="case-filter-query"
                @change="(value: string | number) => applyFilter('q', String(value))"
            />
            <KsSelect
                :modelValue="statusFilter"
                multiple
                clearable
                :placeholder="$t('casesPage.status')"
                data-test="case-filter-status"
                @update:model-value="(value: string[]) => applyFilter('status', value.join(','))"
            >
                <KsOption v-for="status in CASE_STATUSES" :key="status" :label="status" :value="status" />
            </KsSelect>
            <KsSelect
                :modelValue="String(route.query.severity ?? '')"
                clearable
                :placeholder="$t('casesPage.severity')"
                data-test="case-filter-severity"
                @update:model-value="(value: string) => applyFilter('severity', value ?? '')"
            >
                <KsOption v-for="severity in CASE_SEVERITIES" :key="severity" :label="severity" :value="severity" />
            </KsSelect>
        </div>
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="cases"
            :total="total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :selectable="false"
            data-test="cases"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
        >
            <KsTableColumn :label="$t('casesPage.title')" minWidth="240">
                <template #default="scope">
                    <router-link :to="{name: 'cases/update', params: {id: (scope.row as Case).id}}">
                        {{ (scope.row as Case).title }}
                    </router-link>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('casesPage.status')">
                <template #default="scope">
                    <KsTag :type="STATUS_TAG[(scope.row as Case).status]">
                        {{ (scope.row as Case).status }}
                    </KsTag>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('casesPage.severity')">
                <template #default="scope">
                    <KsTag :type="SEVERITY_TAG[(scope.row as Case).severity]" effect="plain">
                        {{ (scope.row as Case).severity }}
                    </KsTag>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('casesPage.dueDate')">
                <template #default="scope">
                    <CaseSla :value="scope.row as Case" />
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('casesPage.assignees')">
                <template #default="scope">
                    {{ (scope.row as Case).assignees.join(", ") }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('flow')">
                <template #default="scope">
                    <span v-if="(scope.row as Case).flowId">{{ (scope.row as Case).namespace }}.{{ (scope.row as Case).flowId }}</span>
                    <span v-else>{{ (scope.row as Case).namespace }}</span>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('executions')" align="right">
                <template #default="scope">
                    {{ (scope.row as Case).executions.length }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('casesPage.updated')">
                <template #default="scope">
                    <KsDateAgo :inverted="true" :date="(scope.row as Case).updated" />
                </template>
            </KsTableColumn>
        </KsDataTable>
    </section>

    <CaseForm v-model="formVisible" :initial="formInitial" @saved="onCreated" />
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
    import TopNavBar from "../layout/TopNavBar.vue"
    import CaseForm from "./CaseForm.vue"
    import CaseSla from "./CaseSla.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {apiUrl} from "override/utils/route"
    import {CASE_SEVERITIES, CASE_STATUSES, SEVERITY_TAG, STATUS_TAG, type Case, type CaseDraft} from "./caseTypes"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const axios = useClient()
    const dataTable = useTemplateRef("dataTable")

    const cases = ref<Case[]>([])
    const total = ref(0)
    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)
    const statusFilter = computed(() => String(route.query.status ?? "").split(",").filter(Boolean))
    const formVisible = ref(false)
    const formInitial = ref<Partial<CaseDraft>>()

    async function loadData({page, size}: {page: number; size: number}) {
        const params = new URLSearchParams({page: String(page), size: String(size)})
        if (route.query.q) params.set("q", String(route.query.q))
        if (route.query.severity) params.set("severity", String(route.query.severity))
        statusFilter.value.forEach(status => params.append("status", status))
        const response = await axios.get<{results: Case[]; total: number}>(`${apiUrl()}/cases/search?${params}`)
        cases.value = response.data.results
        total.value = response.data.total
    }

    function applyFilter(key: string, value: string) {
        const {[key]: _previous, page: _page, ...rest} = route.query
        router.push({query: value ? {...rest, [key]: value} : rest})
    }

    function openForm(initial?: Partial<CaseDraft>) {
        formInitial.value = initial
        formVisible.value = true
    }

    function onCreated(created: Case) {
        router.push({name: "cases/update", params: {id: created.id}})
    }

    const filterKey = computed(() => JSON.stringify([route.query.q, route.query.status, route.query.severity]))
    watch(filterKey, () => dataTable.value?.resetAndReload())

    const routeInfo = computed(() => ({title: t("demos.cases.label")}))
    useRouteContext(routeInfo)

    onMounted(() => {
        const executionId = route.query.executionId
        if (!executionId) return
        const flowId = route.query.flowId ? String(route.query.flowId) : undefined
        openForm({
            title: t("casesPage.fromExecutionTitle", {flow: flowId ?? "", id: String(executionId)}),
            severity: "HIGH",
            namespace: route.query.namespace ? String(route.query.namespace) : undefined,
            flowId,
            autoLink: !!flowId,
            executionIds: [String(executionId)],
        })
    })
</script>

<style scoped>
    .case-filters {
        display: grid;
        grid-template-columns: 2fr 1fr 1fr;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-3);
        max-width: 60rem;
    }
</style>
