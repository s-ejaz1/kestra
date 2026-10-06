<template>
    <div class="iam-toolbar">
        <KsButton type="primary" data-test="iam-add-user" @click="openCreate">
            {{ $t("iamPage.add_user") }}
        </KsButton>
    </div>

    <KsTable :data="users" stripe data-test="iam-users">
        <KsTableColumn prop="email" :label="$t('email')" />
        <KsTableColumn :label="$t('name')">
            <template #default="scope">
                {{ [scope.row.firstName, scope.row.lastName].filter(Boolean).join(" ") }}
            </template>
        </KsTableColumn>
        <KsTableColumn :label="$t('state')">
            <template #default="scope">
                <KsTag :type="scope.row.disabled ? 'info' : 'success'">
                    {{ scope.row.disabled ? $t("disabled") : $t("enabled") }}
                </KsTag>
            </template>
        </KsTableColumn>
        <KsTableColumn :label="$t('actions')" align="right">
            <template #default="scope">
                <KsIconButton :aria-label="$t('edit')" :title="$t('edit')" @click="openEdit(scope.row as IamUser)">
                    <Pencil />
                </KsIconButton>
                <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove(scope.row as IamUser)">
                    <Delete />
                </KsIconButton>
            </template>
        </KsTableColumn>
    </KsTable>

    <KsDialog v-model="dialogVisible" :title="editing ? $t('edit') : $t('iamPage.add_user')" :dirty="isDirty" destroyOnClose appendToBody>
        <KsForm labelPosition="top" @submit.prevent>
            <KsFormItem :label="$t('email')" required>
                <KsInput v-model="form.email" :disabled="editing !== undefined" data-test="iam-user-email" />
            </KsFormItem>
            <KsFormItem :label="$t('iamPage.first_name')">
                <KsInput v-model="form.firstName" />
            </KsFormItem>
            <KsFormItem :label="$t('iamPage.last_name')">
                <KsInput v-model="form.lastName" />
            </KsFormItem>
            <KsFormItem :label="$t('password')" :required="editing === undefined">
                <KsInput v-model="form.password" type="password" showPassword data-test="iam-user-password" />
                <KsText v-if="editing" size="small" type="info">
                    {{ $t("iamPage.password_unchanged_hint") }}
                </KsText>
            </KsFormItem>
            <KsFormItem v-if="editing">
                <KsSwitch v-model="form.disabled" :activeText="$t('disabled')" />
            </KsFormItem>
        </KsForm>
        <template #footer>
            <KsButton @click="dialogVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" data-test="iam-user-save" @click="save">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useClient} from "@kestra-io/kestra-sdk"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import {apiUrl} from "override/utils/route"
    import {useToast} from "../../../utils/toast"
    import type {IamUser, Paged} from "./iamTypes"

    const {t} = useI18n()
    const toast = useToast()
    const axios = useClient()

    const users = ref<IamUser[]>([])
    const dialogVisible = ref(false)
    const editing = ref<IamUser>()
    const emptyForm = () => ({email: "", firstName: "", lastName: "", password: "", disabled: false})
    const form = ref(emptyForm())
    const baseline = ref("")
    const isDirty = computed(() => JSON.stringify(form.value) !== baseline.value)

    async function load() {
        const response = await axios.get<Paged<IamUser>>(`${apiUrl()}/iam/users`)
        users.value = response.data.results
    }

    function openCreate() {
        editing.value = undefined
        form.value = emptyForm()
        baseline.value = JSON.stringify(form.value)
        dialogVisible.value = true
    }

    function openEdit(user: IamUser) {
        editing.value = user
        form.value = {email: user.email, firstName: user.firstName ?? "", lastName: user.lastName ?? "", password: "", disabled: user.disabled}
        baseline.value = JSON.stringify(form.value)
        dialogVisible.value = true
    }

    async function save() {
        const {email, firstName, lastName, password, disabled} = form.value
        if (editing.value) {
            await axios.put(`${apiUrl()}/iam/users/${editing.value.id}`, {firstName, lastName, disabled, password: password || null})
        } else {
            await axios.post(`${apiUrl()}/iam/users`, {email, firstName, lastName, password})
        }
        toast.saved(email)
        dialogVisible.value = false
        await load()
    }

    function remove(user: IamUser) {
        toast.confirm(t("delete confirm", {name: user.email}), async () => {
            await axios.delete(`${apiUrl()}/iam/users/${user.id}`)
            toast.deleted(user.email)
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
