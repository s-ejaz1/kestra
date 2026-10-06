import {defineStore} from "pinia"
import {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"

export interface Grant {
    namespace?: string | null;
    permissions: Record<string, string[]>;
}

export interface MeData {
    id: string;
    email: string;
    superAdmin: boolean;
    grants: Grant[];
}

/**
 * The permissions of the logged-in user. Until they are loaded every check passes, so that a page rendered before
 * the first load is not emptied; the server enforces the permissions anyway.
 */
export class Me {
    constructor(readonly data?: MeData) {
    }

    get email() {
        return this.data?.email
    }

    get isSuperAdmin() {
        return this.data === undefined || this.data.superAdmin
    }

    hasAny(permission: string, namespace?: string) {
        return this.matches(namespace, (grant) => (grant.permissions[permission] ?? []).length > 0)
    }

    hasAnyAction(permission: string, action: string, namespace?: string) {
        return this.isAllowed(permission, action, namespace)
    }

    isAllowed(permission: string, action: string, namespace?: string) {
        return this.matches(namespace, (grant) => (grant.permissions[permission] ?? []).includes(action))
    }

    isAllowedGlobal(permission: string, action: string) {
        if (this.isSuperAdmin) return true
        return this.data!.grants.some(grant => !grant.namespace && (grant.permissions[permission] ?? []).includes(action))
    }

    hasAnyActionOnAnyNamespace(permission: string, action: string) {
        return this.isAllowed(permission, action)
    }

    hasAnyRole() {
        return this.isSuperAdmin || this.data!.grants.length > 0
    }

    getNamespacesForAction(permission: string, action: string): string[] {
        if (this.data === undefined) return []
        return this.data.grants
            .filter(grant => grant.namespace && (grant.permissions[permission] ?? []).includes(action))
            .map(grant => grant.namespace as string)
    }

    // Without a namespace, a grant on any namespace is enough.
    private matches(namespace: string | undefined, predicate: (grant: Grant) => boolean) {
        if (this.isSuperAdmin) return true
        return this.data!.grants
            .filter(grant => !grant.namespace || namespace === undefined || namespace === grant.namespace || namespace.startsWith(`${grant.namespace}.`))
            .some(predicate)
    }
}

export interface AuthMethods {
    mailsEnabled?: boolean;
    passwordless?: boolean;
    loginPassword?: boolean;
    oauths?: string[];
}

export const useAuthStore = defineStore("auth", {
    state: () => ({
        user: new Me() as Me | undefined,
        isLogged: true,
        auths: undefined as AuthMethods | undefined,
    }),
    actions: {
        async loadMe() {
            const response = await useClient().get<MeData>(`${apiUrl()}/me`)
            this.user = new Me(response.data)
        },
        logout(){
            this.user = new Me()
            return Promise.resolve(true)
        },
        correction(){
            return Promise.resolve(true)
        },
        loadAuths(_options: Record<string, unknown>): Promise<AuthMethods | undefined> {
            return Promise.resolve(undefined)
        },
    },
})
