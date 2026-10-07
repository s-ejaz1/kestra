<template>
    <NavBarAction
        v-if="isAllowed"
        :icon="AlertCircleOutline"
        :to="openCaseRoute"
        data-test="execution-open-case"
    >
        {{ $t("casesPage.openFromExecution") }}
    </NavBarAction>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {State} from "@kestra-io/design-system"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import NavBarAction from "../layout/NavBarAction.vue"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import type {Execution} from "../../stores/executions"

    const props = defineProps<{execution: Execution}>()

    const isAllowed = computed(() =>
        props.execution.state?.current === State.FAILED
        && useAuthStore().user?.isAllowed(resource.CASE, action.CREATE, props.execution.namespace),
    )

    const openCaseRoute = computed(() => ({
        name: "cases/list",
        query: {executionId: props.execution.id, namespace: props.execution.namespace, flowId: props.execution.flowId},
    }))
</script>
