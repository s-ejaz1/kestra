<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <KsButton type="primary" data-test="test-suite-create" @click="openEditor()">
                {{ $t("testsPage.create") }}
            </KsButton>
        </template>
    </TopNavBar>
    <section class="full-container">
        <KsTable :data="testSuites" stripe data-test="test-suites">
            <KsTableColumn :label="$t('id')">
                <template #default="scope">
                    {{ (scope.row as TestSuiteWithLastRun).testSuite.id }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('namespace')">
                <template #default="scope">
                    {{ (scope.row as TestSuiteWithLastRun).testSuite.namespace }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('flow')">
                <template #default="scope">
                    {{ (scope.row as TestSuiteWithLastRun).testSuite.flowId }}
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('testsPage.lastRun')">
                <template #default="scope">
                    <KsButton
                        v-if="(scope.row as TestSuiteWithLastRun).lastRun"
                        link
                        data-test="test-suite-last-run"
                        @click="showResults((scope.row as TestSuiteWithLastRun).lastRun!)"
                    >
                        <KsTag :type="TEST_STATE_TAG[(scope.row as TestSuiteWithLastRun).lastRun!.state]">
                            {{ (scope.row as TestSuiteWithLastRun).lastRun!.state }}
                        </KsTag>
                    </KsButton>
                    <KsText v-else type="info">
                        {{ $t("testsPage.never") }}
                    </KsText>
                </template>
            </KsTableColumn>
            <KsTableColumn :label="$t('actions')" align="right">
                <template #default="scope">
                    <KsButton
                        size="small"
                        :loading="running === (scope.row as TestSuiteWithLastRun).testSuite.id"
                        data-test="test-suite-run"
                        @click="run((scope.row as TestSuiteWithLastRun).testSuite)"
                    >
                        {{ $t("testsPage.run") }}
                    </KsButton>
                    <KsIconButton :aria-label="$t('edit')" :title="$t('edit')" @click="openEditor((scope.row as TestSuiteWithLastRun).testSuite)">
                        <Pencil />
                    </KsIconButton>
                    <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove((scope.row as TestSuiteWithLastRun).testSuite)">
                        <Delete />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsTable>
    </section>

    <KsDialog v-model="editorVisible" :title="editing ? $t('edit') : $t('testsPage.create')" :dirty="isDirty" destroyOnClose appendToBody width="900px">
        <KsEditor
            v-bind="editorBindings"
            v-model="source"
            lang="yaml"
            :options="{fullHeight: false, lineNumbers: true}"
            :navbar="false"
            data-test="test-suite-editor"
        />
        <template #footer>
            <KsButton @click="editorVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" data-test="test-suite-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>

    <KsDrawer v-model="resultsVisible" :title="results ? $t('testsPage.results', {id: results.testSuiteId}) : ''" size="50%">
        <TestRunResults v-if="results" :run="results" />
    </KsDrawer>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useClient} from "@kestra-io/kestra-sdk"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import TestRunResults from "./TestRunResults.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../utils/toast"
    import {TEST_STATE_TAG, TEST_SUITE_TEMPLATE, type TestSuite, type TestSuiteRunResult, type TestSuiteWithLastRun} from "./testTypes"

    const {t} = useI18n()
    const toast = useToast()
    const axios = useClient()
    const editorBindings = useEditorBindings()

    const testSuites = ref<TestSuiteWithLastRun[]>([])
    const editorVisible = ref(false)
    const editing = ref<TestSuite>()
    const source = ref("")
    const baseline = ref("")
    const isDirty = computed(() => source.value !== baseline.value)
    const running = ref<string>()
    const results = ref<TestSuiteRunResult>()
    const resultsVisible = ref(false)

    async function load() {
        const response = await axios.get<{results: TestSuiteWithLastRun[]}>(`${apiUrl()}/tests`)
        testSuites.value = response.data.results
    }

    function openEditor(testSuite?: TestSuite) {
        editing.value = testSuite
        source.value = testSuite?.source ?? TEST_SUITE_TEMPLATE
        baseline.value = source.value
        editorVisible.value = true
    }

    async function save() {
        const headers = {"Content-Type": "application/x-yaml"}
        const response = editing.value
            ? await axios.put<TestSuite>(`${apiUrl()}/tests/${editing.value.namespace}/${editing.value.id}`, source.value, {headers})
            : await axios.post<TestSuite>(`${apiUrl()}/tests`, source.value, {headers})
        toast.saved(response.data.id)
        editorVisible.value = false
        await load()
    }

    async function run(testSuite: TestSuite) {
        running.value = testSuite.id
        try {
            const response = await axios.post<TestSuiteRunResult>(`${apiUrl()}/tests/${testSuite.namespace}/${testSuite.id}/run`)
            showResults(response.data)
            await load()
        } finally {
            running.value = undefined
        }
    }

    function showResults(run: TestSuiteRunResult) {
        results.value = run
        resultsVisible.value = true
    }

    function remove(testSuite: TestSuite) {
        toast.confirm(t("delete confirm", {name: testSuite.id}), async () => {
            await axios.delete(`${apiUrl()}/tests/${testSuite.namespace}/${testSuite.id}`)
            toast.deleted(testSuite.id)
            await load()
        })
    }

    const routeInfo = computed(() => ({title: t("demos.tests.label")}))
    useRouteContext(routeInfo)

    onMounted(load)
</script>
