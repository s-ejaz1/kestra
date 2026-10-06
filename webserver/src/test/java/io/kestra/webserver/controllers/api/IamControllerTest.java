package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.models.iam.IamRole;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.utils.IdUtils;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@KestraTest
class IamControllerTest {
    private static final String PASSWORD = "Passw0rdLong";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldGrantBoundRoleOnlyOnItsNamespace() {
        IamController.ApiUser user = createUser();
        client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(user.id(), "viewer", "company.team")),
            IamBinding.class
        );

        IamController.Me me = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/me").basicAuth(user.email(), PASSWORD), IamController.Me.class);

        assertThat(me.superAdmin()).isFalse();
        assertThat(me.grants()).hasSize(1);
        assertThat(me.grants().getFirst().namespace()).isEqualTo("company.team");
        assertThat(me.grants().getFirst().permissions().get(Permission.FLOW)).contains(Action.VIEW).doesNotContain(Action.UPDATE);
    }

    @Test
    void shouldForbidIamManagementWithoutPermission() {
        IamController.ApiUser user = createUser();

        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/iam/users").basicAuth(user.email(), PASSWORD))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void shouldAllowIamManagementOnceAdminRoleIsBound() {
        IamController.ApiUser user = createUser();
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/iam/bindings", new IamController.BindingRequest(user.id(), "admin", null)), IamBinding.class);

        HttpStatus status = client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/iam/users").basicAuth(user.email(), PASSWORD)).getStatus();

        assertThat(status.getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldRejectWrongPasswordAndDisabledUser() {
        IamController.ApiUser user = createUser();

        HttpClientResponseException wrongPassword = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/me").basicAuth(user.email(), "Wr0ngPassword"))
        );
        assertThat(wrongPassword.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

        client.toBlocking().retrieve(
            HttpRequest.PUT("/api/v1/main/iam/users/" + user.id(), new IamController.UserUpdateRequest(null, null, true, null)),
            IamController.ApiUser.class
        );
        HttpClientResponseException disabled = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/me").basicAuth(user.email(), PASSWORD))
        );
        assertThat(disabled.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldLogInWithUserCredentials() {
        IamController.ApiUser user = createUser();

        HttpStatus status = client.toBlocking().exchange(HttpRequest.POST("/api/v1/login", new MiscController.LoginRequest(user.email(), PASSWORD))).getStatus();

        assertThat(status.getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
    }

    @Test
    void shouldRejectChangesToBuiltInRole() {
        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().retrieve(
                HttpRequest.PUT("/api/v1/main/iam/roles/admin", new IamController.RoleRequest("Admin", null, Map.of(Permission.FLOW, List.of(Action.VIEW)))),
                IamRole.class
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldRejectWeakPassword() {
        HttpClientResponseException exception = (HttpClientResponseException) catchThrowable(
            () -> client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(IdUtils.create() + "@kestra.io", null, null, "weak")),
                IamController.ApiUser.class
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    private IamController.ApiUser createUser() {
        String email = IdUtils.create().toLowerCase() + "@kestra.io";
        return client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/iam/users", new IamController.UserRequest(email, "Jane", "Doe", PASSWORD)),
            IamController.ApiUser.class
        );
    }
}
