<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="full-container iam-page">
        <KsTabs v-model="tab">
            <KsTabPane :label="$t('iamPage.users')" name="users">
                <IamUsers v-if="tab === 'users'" />
            </KsTabPane>
            <KsTabPane :label="$t('iamPage.roles')" name="roles">
                <IamRoles v-if="tab === 'roles'" />
            </KsTabPane>
            <KsTabPane :label="$t('iamPage.bindings')" name="bindings">
                <IamBindings v-if="tab === 'bindings'" />
            </KsTabPane>
        </KsTabs>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import TopNavBar from "../../layout/TopNavBar.vue"
    import useRouteContext from "../../../composables/useRouteContext"
    import IamUsers from "./IamUsers.vue"
    import IamRoles from "./IamRoles.vue"
    import IamBindings from "./IamBindings.vue"

    const {t} = useI18n()
    const tab = ref("users")

    const routeInfo = computed(() => ({title: t("iam")}))
    useRouteContext(routeInfo)
</script>

<style scoped>
    .iam-page {
        padding: var(--ks-spacing-4);
    }
</style>
