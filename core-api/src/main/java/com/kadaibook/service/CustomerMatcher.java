package com.kadaibook.service;

import com.kadaibook.domain.Customer;
import com.kadaibook.domain.MatchStatus;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CustomerMatcher {

    public Match match(List<Customer> customers, String name) {
        String needle = normalise(name);
        if (needle.isEmpty()) {
            return new Match(MatchStatus.NONE, List.of());
        }
        List<Customer> matches = new ArrayList<>();
        for (Customer customer : customers) {
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(normalise(customer.getDisplayName()));
            if (customer.getAliases() != null) {
                customer.getAliases().forEach(alias -> candidates.add(normalise(alias)));
            }
            if (candidates.contains(needle)) {
                matches.add(customer);
            }
        }
        if (matches.size() == 1) {
            return new Match(MatchStatus.MATCHED, List.copyOf(matches));
        }
        if (matches.size() > 1) {
            return new Match(MatchStatus.AMBIGUOUS, List.copyOf(matches));
        }
        return new Match(MatchStatus.NONE, List.of());
    }

    public static String normalise(String value) {
        if (value == null) {
            return "";
        }
        String folded = Normalizer.normalize(value, Normalizer.Form.NFKD).toLowerCase(java.util.Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean latinBase = false;
        for (int index = 0; index < folded.length(); ) {
            int codePoint = folded.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.getType(codePoint) == Character.NON_SPACING_MARK
                    || Character.getType(codePoint) == Character.COMBINING_SPACING_MARK
                    || Character.getType(codePoint) == Character.ENCLOSING_MARK) {
                if (!latinBase) {
                    builder.appendCodePoint(codePoint);
                }
                continue;
            }
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(codePoint);
                latinBase = Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
            } else {
                latinBase = false;
            }
        }
        return builder.toString();
    }

    public record Match(MatchStatus status, List<Customer> customers) {

        public Customer single() {
            return status == MatchStatus.MATCHED ? customers.get(0) : null;
        }
    }
}
