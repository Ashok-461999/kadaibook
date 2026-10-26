package com.kadaibook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kadaibook.domain.Customer;
import com.kadaibook.domain.MatchStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerMatcherTest {

    private final CustomerMatcher matcher = new CustomerMatcher();

    @Test
    void normalisesCaseSpacingPunctuationAndDiacritics() {
        assertThat(CustomerMatcher.normalise("  Ravi-Kumar  ")).isEqualTo("ravikumar");
        assertThat(CustomerMatcher.normalise("Raví Kumār")).isEqualTo("ravikumar");
        assertThat(CustomerMatcher.normalise(null)).isEmpty();
    }

    @Test
    void matchesAliasesExactlyAfterNormalisation() {
        Customer customer = customer("Ravi Kumar", List.of("R. Kumar"));
        CustomerMatcher.Match match = matcher.match(List.of(customer), "r kumar");
        assertThat(match.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(match.single()).isSameAs(customer);
    }

    @Test
    void reportsAmbiguousWhenTwoCustomersShareANormalisedAlias() {
        Customer first = customer("Ravi Kumar", List.of("RK"));
        Customer second = customer("Ravi K", List.of("RK"));
        CustomerMatcher.Match match = matcher.match(List.of(first, second), "rk");
        assertThat(match.status()).isEqualTo(MatchStatus.AMBIGUOUS);
        assertThat(match.customers()).hasSize(2);
    }

    @Test
    void returnsNoneForBlankOrUnknownNames() {
        assertThat(matcher.match(List.of(customer("Ravi", List.of())), "   ").status())
                .isEqualTo(MatchStatus.NONE);
        assertThat(matcher.match(List.of(customer("Ravi", List.of())), "Lakshmi").status())
                .isEqualTo(MatchStatus.NONE);
    }

    private static Customer customer(String displayName, List<String> aliases) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setDisplayName(displayName);
        customer.setAliases(aliases);
        return customer;
    }
}
