package com.kadaibook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kadaibook.domain.Customer;
import com.kadaibook.domain.EntryStatus;
import com.kadaibook.domain.ExtractedEntry;
import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.LayoutType;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.ReviewedEntry;
import com.kadaibook.domain.ReviewAction;
import com.kadaibook.domain.Shop;
import com.kadaibook.domain.ValueStatus;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {

    private final CustomerMatcher matcher = new CustomerMatcher();
    private final ReconciliationEngine engine = new ReconciliationEngine(matcher);

    @Test
    void appliesOpeningAndCalculatesBeforeReviewIsComplete() {
        Fixture fixture = fixture();
        ReconciliationEngine.Plan plan = engine.calculate(
                fixture.page,
                fixture.run,
                List.of(fixture.entry(1_200_00L, null, "Cement"), fixture.entry(null, 1_000_00L, "Cash")),
                List.of(fixture.ravi),
                Map.of(),
                Map.of(fixture.ravi.getId(), 0L),
                Map.of(fixture.ravi.getId(), 0L),
                List.of());

        assertThat(plan.previews()).hasSize(1);
        assertThat(plan.previews().get(0).openingImported()).isTrue();
        assertThat(plan.previews().get(0).calculatedBalancePaise()).isEqualTo(2_700_00L);
        assertThat(plan.blockers()).extracting(ReconciliationEngine.Blocker::code)
                .contains("review_pending");
        assertThat(plan.canConfirm()).isFalse();
    }

    @Test
    void automaticallyReviewedRowsCanBeConfirmed() {
        Fixture fixture = fixture();
        ExtractedEntry first = fixture.entry(1_200_00L, null, "Cement");
        ExtractedEntry second = fixture.entry(null, 1_000_00L, "Cash");
        Map<UUID, ReviewedEntry> reviews = new HashMap<>();
        reviews.put(first.getId(), review(first, fixture.ravi));
        reviews.put(second.getId(), review(second, fixture.ravi));

        ReconciliationEngine.Plan plan = engine.calculate(
                fixture.page,
                fixture.run,
                List.of(first, second),
                List.of(fixture.ravi),
                reviews,
                Map.of(fixture.ravi.getId(), 0L),
                Map.of(fixture.ravi.getId(), 0L),
                List.of());

        assertThat(plan.blockers()).isEmpty();
        assertThat(plan.confirmationBlockers()).extracting(ReconciliationEngine.Blocker::code)
                .containsExactly("confirmation_missing");
        assertThat(plan.canConfirm()).isTrue();
        assertThat(plan.previews().get(0).calculatedBalancePaise()).isEqualTo(2_700_00L);
        assertThat(plan.previews().get(0).writtenClosingBalancePaise()).isEqualTo(2_700_00L);
    }

    @Test
    void ignoresPageOpeningWhenCustomerAlreadyHasALedger() {
        Fixture fixture = fixture();
        ReconciliationEngine.Plan plan = engine.calculate(
                fixture.page,
                fixture.run,
                List.of(fixture.entry(1_200_00L, null, "Cement")),
                List.of(fixture.ravi),
                Map.of(),
                Map.of(fixture.ravi.getId(), 50_000_00L),
                Map.of(fixture.ravi.getId(), 2L),
                List.of());

        assertThat(plan.previews()).hasSize(1);
        assertThat(plan.previews().get(0).openingImported()).isFalse();
        assertThat(plan.previews().get(0).openingBalancePaise()).isNull();
        assertThat(plan.previews().get(0).calculatedBalancePaise()).isEqualTo(51_200_00L);
    }

    private Fixture fixture() {
        Shop shop = new Shop();
        shop.setId(UUID.randomUUID());
        Customer ravi = new Customer();
        ravi.setId(UUID.randomUUID());
        ravi.setShop(shop);
        ravi.setDisplayName("Ravi Kumar");
        ravi.setAliases(List.of());

        NotebookPage page = new NotebookPage();
        page.setId(UUID.randomUUID());
        page.setShop(shop);
        page.setStatus(PageStatus.NEEDS_REVIEW);
        page.setLayoutType(LayoutType.CUSTOMER_LEDGER);
        page.setCreatedAt(java.time.Instant.now());

        ExtractionRun run = new ExtractionRun();
        run.setId(UUID.randomUUID());
        run.setPage(page);
        run.setStatus(com.kadaibook.domain.RunStatus.SUCCESS);
        run.setPageCustomerName("Ravi Kumar");
        run.setPageCustomerStatus(ValueStatus.READABLE);
        run.setOpeningBalancePaise(2_500_00L);
        run.setClosingBalancePaise(2_700_00L);
        run.setPageIssues(List.of());
        return new Fixture(shop, ravi, page, run);
    }

    private ReviewedEntry review(ExtractedEntry entry, Customer customer) {
        ReviewedEntry review = new ReviewedEntry();
        review.setId(UUID.randomUUID());
        review.setExtractedEntry(entry);
        review.setCustomer(customer);
        review.setAction(ReviewAction.CONFIRMED);
        review.setFinalDate(entry.getEntryDate());
        review.setFinalDateStatus(entry.getDateStatus());
        review.setFinalDescription(entry.getDescription());
        review.setFinalDebitPaise(entry.getDebitPaise());
        review.setFinalCreditPaise(entry.getCreditPaise());
        review.setFinalEntryStatus(entry.getEntryStatus());
        return review;
    }

    private record Fixture(Shop shop, Customer ravi, NotebookPage page, ExtractionRun run) {

        ExtractedEntry entry(Long debit, Long credit, String description) {
            ExtractedEntry entry = new ExtractedEntry();
            entry.setId(UUID.randomUUID());
            entry.setEntryIndex((int) (System.nanoTime() % 1000));
            entry.setEntryDate(LocalDate.of(2024, 11, 2));
            entry.setDateStatus(ValueStatus.READABLE);
            entry.setDescription(description);
            entry.setDebitPaise(debit);
            entry.setCreditPaise(credit);
            entry.setEntryStatus(EntryStatus.ACTIVE);
            entry.setIssues(List.of());
            entry.setRunningBalancePaise(null);
            return entry;
        }
    }
}
