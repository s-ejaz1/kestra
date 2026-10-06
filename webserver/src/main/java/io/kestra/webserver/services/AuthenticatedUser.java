package io.kestra.webserver.services;

import java.util.Optional;

import io.micronaut.http.HttpRequest;

/**
 * The caller of an API request: the basic-auth administrator, who is a super admin, or a user managed through IAM.
 */
public record AuthenticatedUser(String id, String email, boolean superAdmin) {
    public static final String REQUEST_ATTRIBUTE = "kestra.authenticatedUser";
    public static final String SUPER_ADMIN_ID = "superadmin";

    public static AuthenticatedUser superAdmin(String email) {
        return new AuthenticatedUser(SUPER_ADMIN_ID, email, true);
    }

    public static Optional<AuthenticatedUser> from(HttpRequest<?> request) {
        return request.getAttribute(REQUEST_ATTRIBUTE, AuthenticatedUser.class);
    }
}
