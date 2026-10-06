<template>
    <div class="iam-toolbar">
        <KsButton type="primary" data-test="iam-add-role" @click="openCreate">
            {{ $t("iamPage.add_role") }}
        </KsButton>
    </div>

    <KsTable :data="roles" stripe data-test="iam-roles">
        <KsTableColumn prop="name" :label="$t('name')">
            <template #default="scope">
                {{ scope.row.name }}
                <KsTag v-if="scope.row.builtIn" type="info" size="small">
                    {{ $t("iamPage.built_in") }}
                </KsTag>
            </template>
        </KsTableColumn>
        <KsTableColumn prop="description" :label="$t('description')" />
        <KsTableColumn :label="$t('actions')" align="right">
            <template #default="scope">
                <template v-if="!scope.row.builtIn">
                    <KsIconButton :aria-label="$t('edit')" :title="$t('edit')" @click="openEdit(scope.row as IamRole)">
                        <Pencil />
                    </KsIconButton>
                    <KsIconButton :aria-label="$t('delete')" :title="$t('delete')" @click="remove(scope.row as IamRole)">
                        <Delete />
                    </KsIconButton>
                </template>
            </template>
        </KsTableColumn>
    </KsTable>

    <KsDialog v-model="dialogVisible" :title="editing ? $t('edit') : $t('iamPage.add_role')" :dirty="isDirty" destroyOnClose appendToBody width="720px">
        <KsForm labelPosition="top" @submit.prevent>
            <KsFormItem :label="$t('name')" required>
                <KsInput v-model="form.name" data-test="iam-role-name" />
            </KsFormItem>
            <KsFormItem :label="$t('description')">
                <KsInput v-model="form.description" />
            </KsFormItem>
            <KsFormItem :label="$t('iamPage.permissions')">
                <div class="permission-grid">
                    <template v-for="permission in PERMISSIONS" :key="permission">
                        <KsText>{{ permission }}</KsText>
                        <KsSelect
                            v-model="form.permissions[permission]"
                            multiple
                            collapseTags
                            clearable
                            :data-test="`iam-role-permission-${permission}`"
                        >
                            <KsOption v-for="action in ACTIONS" :key="action" :label="action" :value="action" />
                        </KsSelect>
                    </template>
                </div>
            </KsFormItem>
        </KsForm>
        <template #footer>
            <KsButton @click="dialogVisible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" data-test="iam-role-save" @click="save">
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
    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import type {IamRole, Paged} from "./iamTypes"

    const PERMISSIONS = [...Object.values(resource), "USER", "ROLE", "BINDING"]
    const ACTIONS = Object.values(action).filter(name => name !== "MANAGE_MEMBERS" && name !== "MANAGE_GROUP_MEMBERSHIP")

    const {t} = useI18n()
    const toast = useToast()
    const axios = useClient()

    const roles = ref<IamRole[]>([])
    const dialogVisible = ref(false)
    const editing = ref<IamRole>()
    const emptyForm = () => ({name: "", description: "", permissions: {} as Record<string, string[]>})
    const form = ref(emptyForm())
    const baseline = ref("")
    const isDirty = computed(() => JSON.stringify(form.value) !== baseline.value)

    async function load() {
        const response = await axios.get<Paged<IamRole>>(`${apiUrl()}/iam/roles`)
        roles.value = response.data.results
    }

    function openCreate() {
        editing.value = undefined
        form.value = emptyForm()
        baseline.value = JSON.stringify(form.value)
        dialogVisible.value = true
    }

    function openEdit(role: IamRole) {
        editing.value = role
        form.value = {name: role.name, description: role.description ?? "", permissions: {...role.permissions}}
        baseline.value = JSON.stringify(form.value)
        dialogVisible.value = true
    }

    async function save() {
        const permissions = Object.fromEntries(Object.entries(form.value.permissions).filter(([, actions]) => actions?.length > 0))
        const body = {name: form.value.name, description: form.value.description, permissions}
        if (editing.value) {
            await axios.put(`${apiUrl()}/iam/roles/${editing.value.id}`, body)
        } else {
            await axios.post(`${apiUrl()}/iam/roles`, body)
        }
        toast.saved(form.value.name)
        dialogVisible.value = false
        await load()
    }

    function remove(role: IamRole) {
        toast.confirm(t("delete confirm", {name: role.name}), async () => {
            await axios.delete(`${apiUrl()}/iam/roles/${role.id}`)
            toast.deleted(role.name)
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

    .permission-grid {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: var(--ks-spacing-2) var(--ks-spacing-4);
        align-items: center;
        width: 100%;
    }
</style>
