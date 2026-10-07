package io.kestra.core.policies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.kestra.core.models.policies.Policy;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.PebbleUtil;

import jakarta.annotation.Nullable;

/**
 * Applies policies to a flow in its map form: the mutating rules first ({@link Policy.Defaults}, {@link Policy.Labels}), then the
 * checks, so that a value a policy supplies is what the checks see.
 */
public final class PolicyEngine {
    private static final String TYPE_KEY = "type";
    private static final String POLICY_REFS_KEY = "policyRefs";
    private static final String LABELS_KEY = "labels";

    private PolicyEngine() {
    }

    /**
     * @param flow the flow as a map, which is copied rather than changed
     * @param policies the policies in scope of the flow's namespace, whatever their enforcement
     */
    public static Result apply(Map<String, Object> flow, List<Policy> policies) {
        Map<String, Object> copy = deepCopy(flow);
        Result result = new Result(copy, new ArrayList<>(), new ArrayList<>(), new HashSet<>());

        List<Policy> flowWide = policies.stream()
            .filter(policy -> Policy.Enforcement.ENFORCE == policy.getEnforcement()
                || Policy.Enforcement.AUDIT == policy.getEnforcement()
                || (Policy.Enforcement.REFERENCE == policy.getEnforcement() && refs(copy).contains(policy.getId())))
            .toList();
        List<Policy> referenceOnly = policies.stream()
            .filter(policy -> Policy.Enforcement.REFERENCE == policy.getEnforcement() && !flowWide.contains(policy))
            .toList();

        for (Policy policy : flowWide) {
            for (Policy.Rule rule : policy.getRules()) {
                if (rule instanceof Policy.Labels labels) {
                    applyLabels(copy, policy, labels, result);
                }
            }
        }
        walk(copy, flowWide, referenceOnly, "flow", result);
        for (Policy policy : flowWide) {
            for (Policy.Rule rule : policy.getRules()) {
                if (rule instanceof Policy.RequireLabels required) {
                    checkLabels(copy, policy, required, result);
                }
            }
        }
        return result;
    }

    /** Whether {@code type} matches {@code pattern}, an exact type or a prefix ending with {@code *}. */
    public static boolean matches(String pattern, @Nullable String type) {
        if (type == null || pattern == null) {
            return false;
        }
        if (pattern.endsWith("*")) {
            return type.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(type);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, List<Policy> active, List<Policy> referenceOnly, String path, Result result) {
        if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                walk(list.get(i), active, referenceOnly, path + "[" + i + "]", result);
            }
            return;
        }
        if (!(node instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        List<Policy> scoped = active;
        String here = path;
        if (map.get(TYPE_KEY) instanceof String type && type.contains(".")) {
            List<String> refs = refs(map);
            if (!refs.isEmpty()) {
                scoped = new ArrayList<>(active);
                referenceOnly.stream().filter(policy -> refs.contains(policy.getId()) && !active.contains(policy)).forEach(scoped::add);
            }
            here = Optional.ofNullable(map.get("id")).map(id -> "'" + id + "'").orElse(path);
            applyToPlugin(map, type, scoped, here, result);
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            walk(entry.getValue(), scoped, referenceOnly, here + "." + entry.getKey(), result);
        }
    }

    private static void applyToPlugin(Map<String, Object> plugin, String type, List<Policy> policies, String where, Result result) {
        for (Policy policy : policies) {
            for (Policy.Rule rule : policy.getRules()) {
                if (rule instanceof Policy.Defaults defaults && matches(defaults.target(), type) && defaults.values() != null) {
                    defaults.values().forEach((key, value) -> {
                        if (defaults.override() || !plugin.containsKey(key)) {
                            if (!Objects.equals(plugin.get(key), value)) {
                                plugin.put(key, deepCopyValue(value));
                                result.changes().add(new Change(policy.getId(), where, key, value));
                            }
                        }
                    });
                }
            }
        }
        for (Policy policy : policies) {
            for (Policy.Rule rule : policy.getRules()) {
                if (rule instanceof Policy.BlockPlugins blocked && blocked.plugins() != null
                    && blocked.plugins().stream().anyMatch(pattern -> matches(pattern, type))) {
                    result.violate(policy, "%s uses the plugin '%s', which policy '%s' blocks.".formatted(where, type, policy.getId()));
                }
                if (rule instanceof Policy.PropertyBounds bounds && matches(bounds.target(), type)) {
                    checkBounds(plugin, where, policy, bounds, result);
                }
            }
        }
    }

    private static void checkBounds(Map<String, Object> plugin, String where, Policy policy, Policy.PropertyBounds bounds, Result result) {
        Object value = read(plugin, bounds.property());
        if (value == null) {
            if (bounds.required()) {
                result.violate(policy, "%s must set '%s', as policy '%s' requires.".formatted(where, bounds.property(), policy.getId()));
            }
            return;
        }
        String text = String.valueOf(value);
        if (value instanceof String string && PebbleUtil.containsOpeningBlockDelimiter(string)) {
            return;
        }
        if (bounds.allowedValues() != null && !bounds.allowedValues().isEmpty() && !bounds.allowedValues().contains(text)) {
            result.violate(policy, "%s sets '%s' to '%s', but policy '%s' only allows %s.".formatted(where, bounds.property(), text, policy.getId(), bounds.allowedValues()));
        }
        if (bounds.pattern() != null) {
            try {
                if (!Pattern.compile(bounds.pattern()).matcher(text).matches()) {
                    result.violate(policy, "%s sets '%s' to '%s', which does not match the pattern '%s' of policy '%s'.".formatted(where, bounds.property(), text, bounds.pattern(), policy.getId()));
                }
            } catch (PatternSyntaxException e) {
                result.violate(policy, "Policy '%s' has an invalid pattern '%s'.".formatted(policy.getId(), bounds.pattern()));
            }
        }
        if (bounds.min() != null || bounds.max() != null) {
            Double number = number(value);
            if (number == null) {
                result.violate(policy, "%s sets '%s' to '%s', which is not a number as policy '%s' expects.".formatted(where, bounds.property(), text, policy.getId()));
            } else if ((bounds.min() != null && number < bounds.min()) || (bounds.max() != null && number > bounds.max())) {
                result.violate(policy, "%s sets '%s' to %s, outside the range [%s, %s] of policy '%s'.".formatted(
                    where, bounds.property(), text, Objects.toString(bounds.min(), "-"), Objects.toString(bounds.max(), "-"), policy.getId()));
            }
        }
    }

    private static void applyLabels(Map<String, Object> flow, Policy policy, Policy.Labels rule, Result result) {
        if (rule.labels() == null) {
            return;
        }
        Map<String, String> labels = labels(flow);
        rule.labels().forEach((key, value) -> {
            if (!labels.containsKey(key) || (rule.override() && !Objects.equals(labels.get(key), value))) {
                labels.put(key, value);
                result.changes().add(new Change(policy.getId(), "flow", "labels." + key, value));
            }
            if (rule.override()) {
                result.pinnedLabelKeys().add(key);
            }
        });
        flow.put(LABELS_KEY, labels);
    }

    private static void checkLabels(Map<String, Object> flow, Policy policy, Policy.RequireLabels rule, Result result) {
        Map<String, String> labels = labels(flow);
        for (String key : Optional.ofNullable(rule.keys()).orElse(List.of())) {
            if (!labels.containsKey(key)) {
                result.violate(policy, "The flow needs the label '%s', as policy '%s' requires.".formatted(key, policy.getId()));
            }
        }
        Optional.ofNullable(rule.values()).orElse(Map.of()).forEach((key, allowed) -> {
            String value = labels.get(key);
            if (value != null && allowed != null && !allowed.contains(value)) {
                result.violate(policy, "The label '%s' is '%s', but policy '%s' only allows %s.".formatted(key, value, policy.getId(), allowed));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> labels(Map<String, Object> flow) {
        Map<String, String> labels = new LinkedHashMap<>();
        Object raw = flow.get(LABELS_KEY);
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> labels.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
        } else if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> label && label.get("key") != null) {
                    Object value = ((Map<String, Object>) label).get("value");
                    labels.put(String.valueOf(label.get("key")), value == null ? null : String.valueOf(value));
                }
            }
        }
        return labels;
    }

    @SuppressWarnings("unchecked")
    private static List<String> refs(Map<String, Object> map) {
        if (map.get(POLICY_REFS_KEY) instanceof List<?> list) {
            return ((List<Object>) list).stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Object read(Map<String, Object> plugin, String path) {
        Object current = plugin;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    @Nullable
    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> deepCopy(Map<String, Object> map) {
        return JacksonMapper.toMap(map);
    }

    private static Object deepCopyValue(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?> ? JacksonMapper.toMap(Map.of("v", value)).get("v") : value;
    }

    /**
     * What applying the policies did to the flow.
     *
     * @param flow the flow with the policies applied
     * @param pinnedLabelKeys the labels a policy forced, which an execution may not set
     */
    public record Result(Map<String, Object> flow, List<Change> changes, List<Violation> violations, Set<String> pinnedLabelKeys) {
        void violate(Policy policy, String message) {
            violations.add(new Violation(policy.getId(), Policy.Enforcement.AUDIT == policy.getEnforcement(), message));
        }

        public List<String> blocking() {
            return violations.stream().filter(violation -> !violation.audit()).map(Violation::message).distinct().toList();
        }

        public List<String> audits() {
            return violations.stream().filter(Violation::audit).map(violation -> "[audit] " + violation.message()).distinct().toList();
        }

        public List<String> notices() {
            return changes.stream().map(Change::describe).toList();
        }
    }

    public record Change(String policyId, String target, String property, Object value) {
        public String describe() {
            return "Policy '%s' sets '%s' on %s to %s.".formatted(policyId, property, target, value);
        }
    }

    /**
     * @param audit whether the policy only reports it rather than blocking the flow
     */
    public record Violation(String policyId, boolean audit, String message) {
    }
}
