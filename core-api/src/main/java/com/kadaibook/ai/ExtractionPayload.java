package com.kadaibook.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record ExtractionPayload(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("page_id") String pageId,
        @JsonProperty("layout_type") String layoutType,
        @JsonProperty("currency") String currency,
        @JsonProperty("customer_name") NameValue customerName,
        @JsonProperty("opening_balance_paise") Long openingBalancePaise,
        @JsonProperty("closing_balance_paise") Long closingBalancePaise,
        @JsonProperty("entries") List<Entry> entries,
        @JsonProperty("page_issues") List<String> pageIssues,
        @JsonProperty("review_required") boolean reviewRequired
) {

    public record NameValue(String value, String status) {
    }

    public record Entry(
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("date") LocalDate date,
            @JsonProperty("date_status") String dateStatus,
            @JsonProperty("description") String description,
            @JsonProperty("debit_paise") Long debitPaise,
            @JsonProperty("credit_paise") Long creditPaise,
            @JsonProperty("entry_status") String entryStatus,
            @JsonProperty("source_text") String sourceText,
            @JsonProperty("issues") List<String> issues,
            @JsonProperty("running_balance_paise") Long runningBalancePaise,
            @JsonProperty("customer_name") NameValue customerName
    ) {
    }
}
