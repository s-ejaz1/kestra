export interface IamUser {
    id: string;
    email: string;
    firstName?: string;
    lastName?: string;
    disabled: boolean;
}

export interface IamRole {
    id: string;
    name: string;
    description?: string;
    permissions: Record<string, string[]>;
    builtIn: boolean;
}

export interface IamBinding {
    id: string;
    userId: string;
    roleId: string;
    namespace?: string;
}

export interface Paged<T> {
    results: T[];
    total: number;
}
