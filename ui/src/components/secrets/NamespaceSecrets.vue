<template>
    <SecretsTable
        v-model:addSecretModalVisible="namespacesStore.addSecretModalVisible"
        :namespace="namespace"
        namespaceColumn
        includeInherited
    />
</template>

<script setup lang="ts">
    import SecretsTable from "./SecretsTable.vue"
    import {useNamespacesStore} from "override/stores/namespaces"

    defineProps<{
        namespace: string;
    }>()

    const namespacesStore = useNamespacesStore()
</script>
