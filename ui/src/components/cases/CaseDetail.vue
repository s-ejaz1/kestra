<template>
    <TopNavBar :title="value?.title ?? $t('demos.cases.label')">
        <template v-if="value" #actions>
            <KsButton v-if="value.status === 'OPEN'" data-test="case-acknowledge" @click="changeStatus('ACKNOWLEDGED')">
                {{ $t("casesPage.acknowledge") }}
            </KsButton>
            <KsButton v-if="isOpen(value)" type="primary" data-test="case-resolve" @click="changeStatus('RESOLVED')">
                {{ $t("casesPage.resolve") }}
            </KsButton>
            <KsButton v-if="value.status === 'RESOLVED'" data-test="case-close" @click="changeStatus('CLOSED')">
                {{ $t("casesPage.close") }}
            </KsButton>
            <KsButton v-if="!isOpen(value)" data-test="case-reopen" @click="changeStatus('OPEN')">
                {{ $t("casesPage.reopen") }}
            </KsButton>
            <KsButton :icon="Pencil" data-test="case-edit" @click="formVisible = true">
                {{ $t("edit") }}
            </KsButton>
            <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" data-test="case-delete" @click="remove">
                <Delete />
            </KsIconButton>
        </template>
    </TopNavBar>
    <section class="full-container">
        <KsAlert v-if="loadError" type="error" :closable="false" :title="loadError" />
        <div v-else-if="value" class="case-detail" data-test="case-detail">
            <div class="case-main">
                <KsCard shadow="never">
                    <KsMarkdown v-if="value.description" :content="value.description" />
                    <KsText v-else type="info">
                        {{ $t("casesPage.noDescription") }}
                    </KsText>
                </KsCard>

                <KsCard shadow="never">
                    <template #header>
                        <div class="case-section-header">
                            <KsText tag="strong">
                                {{ $t("executions") }}
                            </KsText>
                            <div class="case-link-input">
                                <KsInput v-model="newExecutionId" size="small" :placeholder="$t('casesPage.executionIdHint')" data-test="case-link-execution-input" />
                                <KsButton size="small" :disabled="!newExecutionId.trim()" data-test="case-link-execution" @click="linkExecution">
                                    {{ $t("casesPage.link") }}
                                </KsButton>
                            </div>
                        </div>
                    </template>
                    <KsTable :data="value.executions" size="small" data-test="case-executions">
                        <KsTableColumn :label="$t('id')">
                            <template #default="scope">
                                <router-link :to="executionRoute(scope.row as LinkedExecution)">
                                    <KsId :value="(scope.row as LinkedExecution).executionId" :shrink="true" />
                                </router-link>
                            </template>
                        </KsTableColumn>
                        <KsTableColumn :label="$t('flow')">
                            <template #default="scope">
                                {{ (scope.row as LinkedExecution).namespace }}.{{ (scope.row as LinkedExecution).flowId }}
                            </template>
                        </KsTableColumn>
                        <KsTableColumn :label="$t('state')">
                            <template #default="scope">
                                <KsExecutionStatus v-if="(scope.row as LinkedExecution).state" :status="(scope.row as LinkedExecution).state!" size="small" />
                            </template>
                        </KsTableColumn>
                        <KsTableColumn :label="$t('casesPage.linked')">
                            <template #default="scope">
                                <KsDateAgo :inverted="true" :date="(scope.row as LinkedExecution).linked" />
                            </template>
                        </KsTableColumn>
                        <KsTableColumn align="right" width="64">
                            <template #default="scope">
                                <KsIconButton :aria-label="$t('casesPage.unlink')" :title="$t('casesPage.unlink')" @click="unlinkExecution(scope.row as LinkedExecution)">
                                    <LinkOff />
                                </KsIconButton>
                            </template>
                        </KsTableColumn>
                    </KsTable>
                </KsCard>

                <KsCard shadow="never">
                    <template #header>
                        <div class="case-section-header">
                            <KsText tag="strong">
                                {{ $t("assets.title") }}
                            </KsText>
                            <div class="case-link-input">
                                <KsInput v-model="newAssetId" size="small" :placeholder="$t('casesPage.assetIdHint')" data-test="case-link-asset-input" />
                                <KsButton size="small" :disabled="!newAssetId.trim()" data-test="case-link-asset" @click="linkAsset">
                                    {{ $t("casesPage.link") }}
                                </KsButton>
                            </div>
                        </div>
                    </template>
                    <div class="case-tags">
                        <KsTag v-for="asset in value.assets" :key="asset" closable @close="unlinkAsset(asset)">
                            <router-link :to="{name: 'assets/update', params: {assetId: asset}}">
                                {{ asset }}
                            </router-link>
                        </KsTag>
                    </div>
                </KsCard>

                <KsCard shadow="never">
                    <template #header>
                        <KsText tag="strong">
                            {{ $t("casesPage.activity") }}
                        </KsText>
                    </template>
                    <KsTimeline data-test="case-timeline">
                        <KsTimelineItem
                            v-for="activity in activities"
                            :key="activity.id"
                            :timestamp="formatDate(activity.date)"
                            :type="activity.type === 'COMMENT' ? 'primary' : undefined"
                        >
                            <KsText tag="strong">
                                {{ activity.author }}
                            </KsText>
                            {{ describe(activity) }}
                            <KsMarkdown v-if="activity.type === 'COMMENT' && activity.message" :content="activity.message" />
                            <KsText v-else-if="activity.type === 'STATUS_CHANGED' && activity.message" tag="p" type="info">
                                {{ activity.message }}
                            </KsText>
                        </KsTimelineItem>
                    </KsTimeline>
                    <div class="case-comment">
                        <KsInput v-model="comment" type="textarea" :rows="3" :placeholder="$t('casesPage.commentHint')" data-test="case-comment-input" />
                        <KsButton type="primary" :disabled="!comment.trim()" data-test="case-comment" @click="addComment">
                            {{ $t("casesPage.comment") }}
                        </KsButton>
                    </div>
                </KsCard>
            </div>

            <KsCard shadow="never" class="case-side">
                <KsDescriptions :column="1">
                    <KsDescriptionsItem :label="$t('casesPage.status')">
                        <KsTag :type="STATUS_TAG[value.status]">
                            {{ value.status }}
                        </KsTag>
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('casesPage.severity')">
                        <KsTag :type="SEVERITY_TAG[value.severity]" effect="plain">
                            {{ value.severity }}
                        </KsTag>
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('casesPage.dueDate')">
                        <CaseSla :value="value" />
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('casesPage.assignees')">
                        {{ value.assignees.join(", ") || "-" }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('labels')">
                        <div class="case-tags">
                            <KsTag v-for="label in value.labels" :key="label" type="info">
                                {{ label }}
                            </KsTag>
                        </div>
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('namespace')">
                        {{ value.namespace ?? "-" }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('flow')">
                        <router-link v-if="value.flowId && value.namespace" :to="{name: 'flows/update', params: {namespace: value.namespace, id: value.flowId}}">
                            {{ value.flowId }}
                        </router-link>
                        <span v-else>-</span>
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('casesPage.autoLink')">
                        {{ value.autoLink ? $t("yes") : $t("no") }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('casesPage.createdBy')">
                        {{ value.createdBy }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('casesPage.opened')">
                        <KsDateAgo :inverted="true" :date="value.created" />
                    </KsDescriptionsItem>
                    <KsDescriptionsItem v-if="value.acknowledgedDate" :label="$t('casesPage.acknowledged')">
                        <KsDateAgo :inverted="true" :date="value.acknowledgedDate" />
                    </KsDescriptionsItem>
                    <KsDescriptionsItem v-if="value.resolvedDate" :label="$t('casesPage.resolved')">
                        <KsDateAgo :inverted="true" :date="value.resolvedDate" />
                    </KsDescriptionsItem>
                </KsDescriptions>
            </KsCard>
        </div>
    </section>

    <CaseForm v-if="value" v-model="formVisible" :initial="draftOf(value)" :editing="value" @saved="onSaved" />
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
    import {dateUtils} from "@kestra-io/design-system"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import LinkOff from "vue-material-design-icons/LinkOff.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import CaseForm from "./CaseForm.vue"
    import CaseSla from "./CaseSla.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../utils/toast"
    import {SEVERITY_TAG, STATUS_TAG, draftOf, isOpen, type Case, type CaseActivity, type CaseStatus, type LinkedExecution} from "./caseTypes"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const axios = useClient()
    const toast = useToast()

    const caseId = computed(() => String(route.params.id))
    const basePath = computed(() => `${apiUrl()}/cases/${caseId.value}`)
    const value = ref<Case>()
    const activities = ref<CaseActivity[]>([])
    const loadError = ref<string>()
    const formVisible = ref(false)
    const comment = ref("")
    const newExecutionId = ref("")
    const newAssetId = ref("")

    async function loadActivities() {
        activities.value = (await axios.get<{results: CaseActivity[]}>(`${basePath.value}/activities?size=500`)).data.results
    }

    async function refresh(updated?: Case) {
        if (updated) value.value = updated
        await loadActivities()
    }

    async function changeStatus(status: CaseStatus) {
        await refresh((await axios.post<Case>(`${basePath.value}/status`, {status})).data)
    }

    async function addComment() {
        await axios.post(`${basePath.value}/comments`, {message: comment.value})
        comment.value = ""
        await refresh()
    }

    async function linkExecution() {
        await refresh((await axios.post<Case>(`${basePath.value}/executions`, {executionId: newExecutionId.value.trim()})).data)
        newExecutionId.value = ""
    }

    async function unlinkExecution(execution: LinkedExecution) {
        await refresh((await axios.delete<Case>(`${basePath.value}/executions/${execution.executionId}`)).data)
    }

    async function linkAsset() {
        await refresh((await axios.post<Case>(`${basePath.value}/assets`, {assetId: newAssetId.value.trim()})).data)
        newAssetId.value = ""
    }

    async function unlinkAsset(assetId: string) {
        await refresh((await axios.delete<Case>(`${basePath.value}/assets/${encodeURIComponent(assetId)}`)).data)
    }

    function remove() {
        if (!value.value) return
        toast.confirm(t("delete confirm", {name: value.value.title}), async () => {
            await axios.delete(basePath.value)
            toast.deleted(value.value?.title ?? caseId.value)
            router.push({name: "cases/list"})
        })
    }

    async function onSaved(updated: Case) {
        await refresh(updated)
    }

    function executionRoute(execution: LinkedExecution) {
        return {name: "executions/update", params: {namespace: execution.namespace, flowId: execution.flowId, id: execution.executionId}}
    }

    function formatDate(date: string) {
        return dateUtils.dateFilter(date)
    }

    function describe(activity: CaseActivity) {
        return t(`casesPage.activities.${activity.type}`, {
            from: activity.from ?? "-",
            to: activity.to ?? "-",
            target: activity.message ?? "",
        })
    }

    const routeInfo = computed(() => ({title: value.value?.title ?? t("demos.cases.label")}))
    useRouteContext(routeInfo)

    onMounted(async () => {
        try {
            value.value = (await axios.get<Case>(basePath.value)).data
            await loadActivities()
        } catch (error) {
            loadError.value = (error as Error).message
        }
    })
</script>

<style scoped>
    .case-detail {
        display: grid;
        grid-template-columns: minmax(0, 3fr) minmax(16rem, 1fr);
        gap: var(--ks-spacing-4);
        align-items: start;
    }

    .case-main {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .case-section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
    }

    .case-link-input {
        display: flex;
        gap: var(--ks-spacing-2);
    }

    .case-tags {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .case-comment {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-4);
    }
</style>
