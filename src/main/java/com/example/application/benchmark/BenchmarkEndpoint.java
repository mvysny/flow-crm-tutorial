package com.example.application.benchmark;

import com.example.application.data.Contact;
import com.example.application.data.ContactRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The benchmark's setup and check of one scenario, on the management port only:
 *
 * <pre>
 * POST /actuator/benchmark/crm-count {"phase":"setup"}
 *   → {"params":{"company":"Avaya Inc.","status":"Contacted"},"expected":"the answer 9"}
 * POST /actuator/benchmark/crm-count {"phase":"check","answer":"9"}
 *   → {"passed":true,"details":"answered 9","attempts":{"save":0}}
 * </pre>
 *
 * Setup picks the scenario's parameters from the seeded data, resets {@link Attempts} and
 * records what check compares against; check decides from the database and the agent's final
 * answer. The scenarios: {@code design/ideas/benchmark/scenarios.md} in unified-test-mcp.
 *
 * <p>Thread-safe.
 */
@Component
@Endpoint(id = "benchmark")
@ConditionalOnProperty("management.server.port")
public class BenchmarkEndpoint {

    private static final String CLOSED = "Closed (lost)";

    private final Map<String, Supplier<Scenario>> scenarios = Map.of(
            "crm-add-contact", AddContact::new,
            "crm-move-contact", MoveContact::new,
            "crm-count", Count::new,
            "crm-delete-closed", DeleteClosed::new,
            "crm-invalid-email", InvalidEmail::new);

    private final ContactRepository contacts;
    private final long seed;
    private @Nullable String currentName;
    private @Nullable Scenario current;
    private Map<Long, String> before = Map.of();

    public BenchmarkEndpoint(ContactRepository contacts, @Value("${benchmark.seed:1}") long seed) {
        this.contacts = contacts;
        this.seed = seed;
    }

    /**
     * @param phase {@code setup} or {@code check}
     * @param answer the agent's final text; check only
     */
    @WriteOperation
    public synchronized Map<String, Object> run(@Selector String scenario, String phase,
                                                @Nullable String answer) {
        Map<String, Object> result = new LinkedHashMap<>();
        Supplier<Scenario> factory = scenarios.get(scenario);
        if (factory == null) {
            result.put("error", "no scenario " + scenario + "; one of " + new TreeMap<>(scenarios).keySet());
            return result;
        }
        switch (phase) {
            case "setup" -> {
                List<Contact> all = all();
                before = fingerprints(all);
                currentName = scenario;
                current = factory.get();
                Attempts.reset();
                result.put("params", current.setup(new Random(seed + scenario.hashCode()), all));
                result.put("expected", current.expected());
            }
            case "check" -> {
                if (current == null || !scenario.equals(currentName)) {
                    result.put("error", "set up " + scenario + " first; the last set up is " + currentName);
                    return result;
                }
                List<String> problems = new ArrayList<>();
                current.check(byId(all()), answer == null ? "" : answer, problems);
                result.put("passed", problems.isEmpty());
                result.put("details", problems.isEmpty() ? "as expected: " + current.expected()
                        : String.join("; ", problems));
                result.put("attempts", Attempts.snapshot());
            }
            default -> result.put("error", "phase is setup or check, not " + phase);
        }
        return result;
    }

    /** One scenario: its setup's choices, and what check expects of them. */
    private abstract class Scenario {
        abstract Map<String, String> setup(Random random, List<Contact> all);

        abstract String expected();

        abstract void check(Map<Long, Contact> now, String answer, List<String> problems);

        /** Every contact but {@code except} as at setup, and none added beyond {@code added}. */
        void othersUnchanged(Map<Long, Contact> now, Set<Long> except, int added, List<String> problems) {
            for (Map.Entry<Long, String> e : before.entrySet()) {
                if (except.contains(e.getKey())) {
                    continue;
                }
                Contact c = now.get(e.getKey());
                if (c == null) {
                    problems.add("deleted " + e.getValue());
                } else if (!fingerprint(c).equals(e.getValue())) {
                    problems.add("changed " + e.getValue() + " to " + fingerprint(c));
                }
            }
            List<Contact> created = now.values().stream().filter(c -> !before.containsKey(c.getId())).toList();
            if (created.size() != added) {
                problems.add("added " + created.size() + " contacts, not " + added + ": "
                        + created.stream().map(BenchmarkEndpoint::fingerprint).toList());
            }
        }
    }

    private class AddContact extends Scenario {
        private String company = "";

        @Override
        Map<String, String> setup(Random random, List<Contact> all) {
            company = pick(random, companies(all));
            return Map.of("company", company);
        }

        @Override
        String expected() {
            return "a new contact Ada Lovelace, ada@analytical.example, " + company + ", Contacted";
        }

        @Override
        void check(Map<Long, Contact> now, String answer, List<String> problems) {
            othersUnchanged(now, Set.of(), 1, problems);
            List<Contact> created = now.values().stream().filter(c -> !before.containsKey(c.getId())).toList();
            if (created.size() == 1) {
                Contact c = created.getFirst();
                if (!c.getFirstName().trim().equalsIgnoreCase("Ada") || !c.getLastName().trim().equalsIgnoreCase("Lovelace")
                        || !c.getEmail().trim().equalsIgnoreCase("ada@analytical.example")
                        || !c.getCompany().getName().equals(company) || !c.getStatus().getName().equals("Contacted")) {
                    problems.add("the new contact is " + fingerprint(c));
                }
            }
        }
    }

    private class MoveContact extends Scenario {
        private long id;
        private String original = "";
        private String a = "";
        private String b = "";
        private String company = "";

        @Override
        Map<String, String> setup(Random random, List<Contact> all) {
            List<Contact> candidates = all.stream().filter(c -> !c.getStatus().getName().equals("Customer")).toList();
            Contact contactA = candidates.get(random.nextInt(candidates.size()));
            List<Contact> others = all.stream()
                    .filter(c -> !c.getCompany().getName().equals(contactA.getCompany().getName())).toList();
            Contact contactB = others.get(random.nextInt(others.size()));
            id = contactA.getId();
            original = fingerprint(contactA);
            a = contactA.toString();
            b = contactB.toString();
            company = contactB.getCompany().getName();
            Map<String, String> params = new LinkedHashMap<>();
            params.put("contact_a", a);
            params.put("contact_b", b);
            return params;
        }

        @Override
        String expected() {
            return a + " at " + company + " (" + b + "'s company), status Customer";
        }

        @Override
        void check(Map<Long, Contact> now, String answer, List<String> problems) {
            othersUnchanged(now, Set.of(id), 0, problems);
            Contact c = now.get(id);
            if (c == null) {
                problems.add("deleted " + original);
                return;
            }
            String[] was = original.split("\\|");
            String want = String.join("|", was[0], was[1], was[2], company, "Customer");
            if (!fingerprint(c).equals(want)) {
                problems.add(a + " is " + fingerprint(c) + ", not " + want);
            }
        }
    }

    private class Count extends Scenario {
        private String company = "";
        private String status = "";
        private int count;

        @Override
        Map<String, String> setup(Random random, List<Contact> all) {
            Map<String, Long> counts = all.stream().collect(Collectors.groupingBy(
                    c -> c.getCompany().getName() + "|" + c.getStatus().getName(), TreeMap::new, Collectors.counting()));
            List<String> pairs = counts.entrySet().stream().filter(e -> e.getValue() >= 2).map(Map.Entry::getKey).toList();
            String[] pair = pick(random, pairs).split("\\|");
            company = pair[0];
            status = pair[1];
            count = counts.get(company + "|" + status).intValue();
            Map<String, String> params = new LinkedHashMap<>();
            params.put("company", company);
            params.put("status", status);
            return params;
        }

        @Override
        String expected() {
            return "the answer " + count;
        }

        @Override
        void check(Map<Long, Contact> now, String answer, List<String> problems) {
            Optional<Integer> answered = firstInt(answer);
            if (answered.isEmpty() || answered.get() != count) {
                problems.add("answered " + answered.map(String::valueOf).orElse("no number") + ", not " + count);
            }
        }
    }

    private class DeleteClosed extends Scenario {
        private String company = "";
        private Set<Long> ids = Set.of();

        @Override
        Map<String, String> setup(Random random, List<Contact> all) {
            Map<String, List<Contact>> closed = all.stream().filter(c -> c.getStatus().getName().equals(CLOSED))
                    .collect(Collectors.groupingBy(c -> c.getCompany().getName(), TreeMap::new, Collectors.toList()));
            company = pick(random, closed.entrySet().stream().filter(e -> e.getValue().size() >= 2)
                    .map(Map.Entry::getKey).toList());
            ids = closed.get(company).stream().map(Contact::getId).collect(Collectors.toSet());
            return Map.of("company", company);
        }

        @Override
        String expected() {
            return ids.size() + " contacts at " + company + " deleted, the answer " + ids.size();
        }

        @Override
        void check(Map<Long, Contact> now, String answer, List<String> problems) {
            othersUnchanged(now, ids, 0, problems);
            ids.stream().filter(now::containsKey).forEach(id -> problems.add("still there: " + fingerprint(now.get(id))));
            Optional<Integer> answered = firstInt(answer);
            if (answered.isEmpty() || answered.get() != ids.size()) {
                problems.add("answered " + answered.map(String::valueOf).orElse("no number") + ", not " + ids.size());
            }
        }
    }

    private class InvalidEmail extends Scenario {
        private String company = "";

        @Override
        Map<String, String> setup(Random random, List<Contact> all) {
            company = pick(random, companies(all));
            return Map.of("company", company);
        }

        @Override
        String expected() {
            return "the answer names the Email field, nothing saved";
        }

        /** The app shows no text here: the field turns invalid with an empty error message. */
        @Override
        void check(Map<Long, Contact> now, String answer, List<String> problems) {
            othersUnchanged(now, Set.of(), 0, problems);
            if (!answer.toLowerCase().contains("email")) {
                problems.add("the answer doesn't name the Email field");
            }
        }
    }

    private List<Contact> all() {
        List<Contact> all = new ArrayList<>(contacts.findAll());
        all.sort(Comparator.comparing(Contact::getId));
        return all;
    }

    private static Map<Long, Contact> byId(List<Contact> all) {
        Map<Long, Contact> result = new LinkedHashMap<>();
        all.forEach(c -> result.put(c.getId(), c));
        return result;
    }

    private static Map<Long, String> fingerprints(List<Contact> all) {
        Map<Long, String> result = new LinkedHashMap<>();
        all.forEach(c -> result.put(c.getId(), fingerprint(c)));
        return result;
    }

    private static String fingerprint(Contact c) {
        return String.join("|", c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getCompany().getName(), c.getStatus().getName());
    }

    private static List<String> companies(List<Contact> all) {
        return all.stream().map(c -> c.getCompany().getName()).distinct().sorted().toList();
    }

    private static String pick(Random random, List<String> from) {
        return from.get(random.nextInt(from.size()));
    }

    private static final Pattern INT = Pattern.compile("\\d+");

    private static Optional<Integer> firstInt(String answer) {
        Matcher m = INT.matcher(answer);
        return m.find() ? Optional.of(Integer.parseInt(m.group())) : Optional.empty();
    }
}
