package io.kestra.webserver.services;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.hash.Hashing;

import io.kestra.core.models.iam.IamUser;
import io.kestra.core.repositories.IamUserRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.AuthUtils;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Resolves the caller of a request: the basic-auth administrator first, then the users managed through IAM.
 */
@Singleton
@Requires(beans = BasicAuthService.class)
public class IamAuthenticationService {
    private static final int MAX_CACHED_TOKENS = 1_000;
    private static final String UNKNOWN_USER_SALT = AuthUtils.generateSalt();

    private final BasicAuthService basicAuthService;
    private final IamUserRepositoryInterface userRepository;
    private final TenantService tenantService;

    /** Verified tokens, so that bcrypt only runs once per token and password hash. */
    private final Map<String, CachedUser> verifiedTokens = new ConcurrentHashMap<>();

    private volatile String unknownUserHash;

    private record CachedUser(AuthenticatedUser user, String passwordHash) {
    }

    @Inject
    public IamAuthenticationService(BasicAuthService basicAuthService, IamUserRepositoryInterface userRepository, TenantService tenantService) {
        this.basicAuthService = basicAuthService;
        this.userRepository = userRepository;
        this.tenantService = tenantService;
    }

    public Optional<AuthenticatedUser> authenticate(HttpRequest<?> request) {
        Optional<String> token = basicAuthService.extractToken(request);
        if (token.isEmpty()) {
            return Optional.empty();
        }

        Optional<String[]> credentials = decode(token.get());
        if (credentials.isEmpty()) {
            return Optional.empty();
        }

        if (basicAuthService.isAuthenticated(request)) {
            return Optional.of(AuthenticatedUser.superAdmin(credentials.get()[0]));
        }

        String tokenKey = Hashing.sha256().hashString(token.get(), StandardCharsets.UTF_8).toString();
        return authenticateUser(credentials.get()[0], credentials.get()[1], tokenKey);
    }

    public Optional<AuthenticatedUser> validateCredentials(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        if (basicAuthService.validateCredentials(username, password)) {
            return Optional.of(AuthenticatedUser.superAdmin(username));
        }
        return authenticateUser(username, password, null);
    }

    private Optional<AuthenticatedUser> authenticateUser(String email, String password, @Nullable String tokenKey) {
        Optional<IamUser> maybeUser = userRepository.findByEmail(tenantService.resolveTenant(), email)
            .filter(user -> !user.isDisabled());

        if (maybeUser.isEmpty()) {
            // Pay the bcrypt cost anyway, so an unknown email cannot be told apart from a wrong password by response time.
            AuthUtils.matches(UNKNOWN_USER_SALT, password, unknownUserHash());
            return Optional.empty();
        }

        IamUser user = maybeUser.get();
        if (tokenKey != null) {
            CachedUser cached = verifiedTokens.get(tokenKey);
            if (cached != null && AuthUtils.constantTimeEquals(cached.passwordHash(), user.getPasswordHash())) {
                return Optional.of(cached.user());
            }
        }

        if (!AuthUtils.matches(user.getPasswordSalt(), password, user.getPasswordHash())) {
            return Optional.empty();
        }

        AuthenticatedUser authenticated = new AuthenticatedUser(user.getId(), user.getEmail(), false);
        if (tokenKey != null) {
            if (verifiedTokens.size() >= MAX_CACHED_TOKENS) {
                verifiedTokens.clear();
            }
            verifiedTokens.put(tokenKey, new CachedUser(authenticated, user.getPasswordHash()));
        }
        return Optional.of(authenticated);
    }

    private String unknownUserHash() {
        if (unknownUserHash == null) {
            unknownUserHash = AuthUtils.hashPassword(UNKNOWN_USER_SALT, "unknown-user");
        }
        return unknownUserHash;
    }

    private static Optional<String[]> decode(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon < 0 ? Optional.empty() : Optional.of(new String[] { decoded.substring(0, colon), decoded.substring(colon + 1) });
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
