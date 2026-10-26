package com.kadaibook.money;

public final class Money {

    public static final long MAX_PAISE = 1_000_000_000_000L;

    private Money() {
    }

    public static boolean isValidAmount(Long paise) {
        return paise != null && paise >= 0 && paise <= MAX_PAISE;
    }

    public static boolean isValidSigned(Long paise) {
        return paise == null || (paise >= -MAX_PAISE && paise <= MAX_PAISE);
    }

    public static long checkedAdd(long left, long right) {
        try {
            long result = Math.addExact(left, right);
            if (result > MAX_PAISE || result < -MAX_PAISE) {
                throw new ArithmeticException("balance outside supported range");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new InvalidMoneyException("amount is outside the supported range");
        }
    }

    public static long amountDelta(Long debitPaise, Long creditPaise) {
        long debit = debitPaise == null ? 0 : debitPaise;
        long credit = creditPaise == null ? 0 : creditPaise;
        return checkedAdd(debit, -credit);
    }

    public static void requireImportableAmount(Long debitPaise, Long creditPaise) {
        if (debitPaise != null && !isValidAmount(debitPaise)) {
            throw new InvalidMoneyException("the debit amount must be zero or greater");
        }
        if (creditPaise != null && !isValidAmount(creditPaise)) {
            throw new InvalidMoneyException("the credit amount must be zero or greater");
        }
        long debit = debitPaise == null ? 0 : debitPaise;
        long credit = creditPaise == null ? 0 : creditPaise;
        if (debit == 0 && credit == 0) {
            throw new InvalidMoneyException("the amount must be greater than zero");
        }
    }

    public static long positiveDebit(Long debitPaise) {
        return debitPaise == null ? 0 : debitPaise;
    }

    public static long positiveCredit(Long creditPaise) {
        return creditPaise == null ? 0 : creditPaise;
    }

    public static class InvalidMoneyException extends RuntimeException {
        public InvalidMoneyException(String message) {
            super(message);
        }
    }
}
