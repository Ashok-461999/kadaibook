package com.kadaibook.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void validAmountsAreNonNegativeAndBounded() {
        assertThat(Money.isValidAmount(0L)).isTrue();
        assertThat(Money.isValidAmount(250000L)).isTrue();
        assertThat(Money.isValidAmount(-1L)).isFalse();
        assertThat(Money.isValidAmount(Money.MAX_PAISE + 1)).isFalse();
    }

    @Test
    void requiresAtLeastOnePositiveSideAndAllowsBoth() {
        Money.requireImportableAmount(100L, null);
        Money.requireImportableAmount(null, 100L);
        Money.requireImportableAmount(100L, 100L);
        Money.requireImportableAmount(100L, 40L);
        assertThatThrownBy(() -> Money.requireImportableAmount(0L, 0L))
                .isInstanceOf(Money.InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.requireImportableAmount(null, null))
                .isInstanceOf(Money.InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.requireImportableAmount(-1L, 100L))
                .isInstanceOf(Money.InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.requireImportableAmount(100L, -1L))
                .isInstanceOf(Money.InvalidMoneyException.class);
    }

    @Test
    void checkedAddRejectsOverflowAndOutOfRangeValues() {
        assertThat(Money.checkedAdd(100L, 200L)).isEqualTo(300L);
        assertThatThrownBy(() -> Money.checkedAdd(Long.MAX_VALUE, 1L))
                .isInstanceOf(Money.InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.checkedAdd(Money.MAX_PAISE, 1L))
                .isInstanceOf(Money.InvalidMoneyException.class);
    }

    @Test
    void amountDeltaUsesDebitMinusCredit() {
        assertThat(Money.amountDelta(1000L, null)).isEqualTo(1000L);
        assertThat(Money.amountDelta(null, 400L)).isEqualTo(-400L);
    }
}
