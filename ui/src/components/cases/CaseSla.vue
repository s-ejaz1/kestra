<template>
    <span v-if="value.dueDate" class="case-sla">
        <KsDateAgo :inverted="true" :date="value.dueDate" />
        <KsTag v-if="isSlaBreached(value)" type="danger" size="small" data-test="case-sla-breached">
            {{ $t("casesPage.slaBreached") }}
        </KsTag>
    </span>
</template>

<script setup lang="ts">
    import {isSlaBreached, type Case} from "./caseTypes"

    defineProps<{value: Case}>()
</script>

<style scoped>
    .case-sla {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }
</style>
