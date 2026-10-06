<template>
    <div class="iam-toolbar">
        <KsButton type="primary" data-test="iam-add-binding" @click="openCreate">
            {{ $t("iamPage.add_binding") }}
        </KsButton>
    </div>

    <KsTable :data="bindings" stripe data-test="iam-bindings">
        <KsTableColumn :label="$t('iamPage.user')">
            <template #default="scope">
                {{ userEmail(scope.row.userId) }}
            </template>
        </KsTableColumn>
        <KsTableColumn :label="$t('iamPage.role')">
            <template #default="scope">
                {{ roleName(scope.row.roleId) }}
            </template>
        </KsTableColumn>
        <KsTableColumn :label="$t('namespace')">
            <template #default="scope">
                {{ scope.row.namespace ?? $t("iamPage.all_namespaces") }}
            </template>
        </KsTableColumn>
        <KsTableColumn :label="$t('actions')" align="right">
            <template #default="scope">
                <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove(scope.row as IamBinding)">
                    <Delete />
                </KsIconButton>
            </template>
        </KsTableColumn>
    </KsTable>

    <KsDialog v-model="dialogVisible" :title="$t('iamPage.add_binding')" :dirty="isDirty" destroyOnClose appendToBody>
        <KsForm labelPosition="top" @submit.prevent>
            <KsFormItem :label="$t('iamPage.user')" required>
                <KsSelect v-model="form.userId" filterable data-test="iam-binding-user">
                    <KsOption v-for="user in users" :key="user.id" :label="user.email" :value="user.id" />
                </KsSelect>
            </KsFormItem>
            <KsFormItem :label="$t('iamPage.role')" required>
                <KsSelect v-model="form.roleId" filterable data-test="iam-binding-role">
                    <KsOption v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
                </KsSelect>
            </KsFormItem>
            <KsFormItem :label="$t('namespace')">
                <KsInput v-model="form.namespace" :placeholder="$t('iamPage.all_namespaces')" data-test="iam-binding-namespace" />
            </KsFormItem>
        </KsForm>
        <template #footer>
            <KsButton @click="dialogVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" :disabled="!form.userId || !form.roleId" data-test="iam-binding-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useClient} from "@kestra-io/kestra-sdk"
    import Delete from "vue-material-design-icons/Delete.vue"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../../utils/toast"
    import type {IamBinding, IamRole, IamUser, Paged} from "./iamTypes"

    const {t} = useI18n()
    const toast = useToast()
    const axios = useClient()

    const bindings = ref<IamBinding[]>([])
    const users = ref<IamUser[]>([])
    const roles = ref<IamRole[]>([])
    const dialogVisible = ref(false)
    const emptyForm = () => ({userId: "", roleId: "", namespace: ""})
    const form = ref(emptyForm())
    const isDirty = computed(() => JSON.stringify(form.value) !== JSON.stringify(emptyForm()))

    const userEmail = (id: string) => users.value.find(user => user.id === id)?.email ?? id
    const roleName = (id: string) => roles.value.find(role => role.id === id)?.name ?? id

    async function load() {
        const [bindingResponse, userResponse, roleResponse] = await Promise.all([
            axios.get<Paged<IamBinding>>(`${apiUrl()}/iam/bindings`),
            axios.get<Paged<IamUser>>(`${apiUrl()}/iam/users`),
            axios.get<Paged<IamRole>>(`${apiUrl()}/iam/roles`),
        ])
        bindings.value = bindingResponse.data.results
        users.value = userResponse.data.results
        roles.value = roleResponse.data.results
    }

    function openCreate() {
        form.value = emptyForm()
        dialogVisible.value = true
    }

    async function save() {
        await axios.post(`${apiUrl()}/iam/bindings`, {...form.value, namespace: form.value.namespace || null})
        toast.saved(userEmail(form.value.userId))
        dialogVisible.value = false
        await load()
    }

    function remove(binding: IamBinding) {
        const name = `${userEmail(binding.userId)} / ${roleName(binding.roleId)}`
        toast.confirm(t("delete confirm", {name}), async () => {
            await axios.delete(`${apiUrl()}/iam/bindings/${binding.id}`)
            toast.deleted(name)
            await load()
        })
    }

    onMounted(load)
</script>

<style scoped>
    .iam-toolbar {
        display: flex;
        justify-content: flex-end;
        margin-bottom: var(--ks-spacing-3);
    }
</style>
