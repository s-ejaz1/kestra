<template>
    <TopNavBar :title="asset?.displayName || assetId" />
    <section class="full-container asset-detail" data-test="asset-detail">
        <KsAlert v-if="loadError" type="error" :closable="false" :title="loadError" />
        <KsTabs v-else-if="asset" v-model="tab">
            <KsTabPane :label="$t('assetsPage.overview')" name="overview">
                <KsDescriptions :column="1" border>
                    <KsDescriptionsItem :label="$t('id')">
                        <KsId :value="asset.id" />
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('type')">
                        {{ asset.type }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('namespace')">
                        {{ asset.namespace }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('description')">
                        {{ asset.description }}
                    </KsDescriptionsItem>
                    <KsDescriptionsItem :label="$t('assetsPage.updated')">
                        <KsDateAgo v-if="asset.updated" :inverted="true" :date="asset.updated" />
                    </KsDescriptionsItem>
                </KsDescriptions>
                <KsJsonTree v-if="asset.metadata && Object.keys(asset.metadata).length" :value="asset.metadata" defaultExpanded />
            </KsTabPane>
            <KsTabPane :label="$t('assetsPage.lineage')" name="lineage" class="asset-lineage">
                <Dependencies v-if="tab === 'lineage'" :fetchAssetDependencies="fetchLineage" dagView />
            </KsTabPane>
            <KsTabPane :label="$t('assetsPage.usages')" name="usages">
                <KsDataTable
                    v-if="tab === 'usages'"
                    v-model:currentPage="usagePage"
                    v-model:pageSize="usageSize"
                    :loadData="loadUsages"
                    :data="usages"
                    :total="usageTotal"
                    :selectable="false"
                    data-test="asset-usages"
                >
                    <KsTableColumn :label="$t('date')">
                        <template #default="scope">
                            <KsDateAgo :inverted="true" :date="(scope.row as AssetUsage).date" />
                        </template>
                    </KsTableColumn>
                    <KsTableColumn :label="$t('assetsPage.direction')">
                        <template #default="scope">
                            <KsTag :type="(scope.row as AssetUsage).direction === 'OUTPUT' ? 'success' : 'info'">
                                {{ (scope.row as AssetUsage).direction }}
                            </KsTag>
                        </template>
                    </KsTableColumn>
                    <KsTableColumn prop="namespace" :label="$t('namespace')" />
                    <KsTableColumn prop="flowId" :label="$t('flow')" />
                    <KsTableColumn prop="taskId" :label="$t('task')" />
                    <KsTableColumn :label="$t('execution')">
                        <template #default="scope">
                            <router-link :to="executionRoute(scope.row as AssetUsage)">
                                <KsId :value="(scope.row as AssetUsage).executionId" :shrink="true" />
                            </router-link>
                        </template>
                    </KsTableColumn>
                </KsDataTable>
            </KsTabPane>
        </KsTabs>
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Dependencies from "../dependencies/Dependencies.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {apiUrl} from "override/utils/route"
    import {lineageElements, type Asset, type AssetLineage, type AssetUsage} from "./assetTypes"

    const route = useRoute()
    const axios = useClient()

    const assetId = computed(() => String(route.params.assetId))
    const assetPath = computed(() => `${apiUrl()}/assets/${encodeURIComponent(assetId.value)}`)
    const asset = ref<Asset>()
    const loadError = ref<string>()
    const tab = ref("overview")
    const usages = ref<AssetUsage[]>([])
    const usageTotal = ref(0)
    const usagePage = ref(1)
    const usageSize = ref(25)

    async function fetchLineage() {
        const response = await axios.get<AssetLineage>(`${assetPath.value}/lineage`)
        return {data: lineageElements(response.data), count: response.data.assets.length}
    }

    async function loadUsages({page, size}: {page: number; size: number}) {
        const response = await axios.get<{results: AssetUsage[]; total: number}>(`${assetPath.value}/usages?page=${page}&size=${size}`)
        usages.value = response.data.results
        usageTotal.value = response.data.total
    }

    function executionRoute(usage: AssetUsage) {
        return {name: "executions/update", params: {namespace: usage.namespace, flowId: usage.flowId, id: usage.executionId}}
    }

    const routeInfo = computed(() => ({title: asset.value?.displayName || assetId.value}))
    useRouteContext(routeInfo)

    onMounted(async () => {
        try {
            asset.value = (await axios.get<Asset>(assetPath.value)).data
        } catch (error) {
            loadError.value = (error as Error).message
        }
    })
</script>

<style scoped>
    .asset-detail {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .asset-lineage {
        height: 70vh;
    }
</style>
