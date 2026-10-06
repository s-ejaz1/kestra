import {describe, expect, it} from "vitest"
import {Me} from "../../../src/override/stores/auth"

const viewerOnTeam = new Me({
    id: "u1",
    email: "jane@kestra.io",
    superAdmin: false,
    grants: [{namespace: "company.team", permissions: {FLOW: ["VIEW"]}}],
})

describe("Me", () => {
    it("allows an action on the bound namespace and its children only", () => {
        expect(viewerOnTeam.isAllowed("FLOW", "VIEW", "company.team")).toBe(true)
        expect(viewerOnTeam.isAllowed("FLOW", "VIEW", "company.team.data")).toBe(true)
        expect(viewerOnTeam.isAllowed("FLOW", "VIEW", "company.teamwork")).toBe(false)
        expect(viewerOnTeam.isAllowed("FLOW", "VIEW", "company")).toBe(false)
    })

    it("denies actions the role does not grant", () => {
        expect(viewerOnTeam.isAllowed("FLOW", "UPDATE", "company.team")).toBe(false)
        expect(viewerOnTeam.isAllowedGlobal("FLOW", "VIEW")).toBe(false)
    })

    it("allows everything for a super admin and before permissions are loaded", () => {
        expect(new Me({id: "superadmin", email: "admin@kestra.io", superAdmin: true, grants: []}).isAllowed("USER", "DELETE")).toBe(true)
        expect(new Me().isAllowed("USER", "DELETE", "any")).toBe(true)
    })
})
