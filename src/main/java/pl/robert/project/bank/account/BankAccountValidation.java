package pl.robert.project.bank.account;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.BindingResult;
import pl.robert.project.transactions.dto.TransactionDTO;
import pl.robert.project.validation.ValidationStrings;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class BankAccountValidation implements ValidationStrings {

    BankAccountRepository repository;

    void checkReceiverBankAccountNumber(long senderId, TransactionDTO dto, BindingResult result) {
        dto.setReceiverAccountNumber(modifyBankAccountNumber(dto.getReceiverAccountNumber()));
        BankAccount bankAccount = repository.findByNumber(dto.getReceiverAccountNumber());

        if (bankAccount == null) {
            result.rejectValue(F_RECEIVER_ACCOUNT_NUMBER, C_RECEIVER_ACCOUNT_NUMBER_NOT_EXISTS);
        }

        if (bankAccount != null && senderId == bankAccount.getId()) {
            result.rejectValue(F_RECEIVER_ACCOUNT_NUMBER, C_RECEIVER_ACCOUNT_NUMBER_MATCH_SENDER);
        }
    }

    private String modifyBankAccountNumber(String numberToCheck) {
        if (numberToCheck.length() == 29) {
            return numberToCheck;
        }

        String string = ("PL").concat(numberToCheck).trim().replace(" ", "");
        if (string.length() == 29) {
            return string;
        }

        StringBuilder sb = new StringBuilder(string);
        if (sb.length() != 24) {
            return numberToCheck;
        }

        for (int i=0; i<29; i++) {
            switch (i) {
                case 4:
                case 9:
                case 14:
                case 19:
                case 24: sb.insert(i, " "); break;
            }
        }
        return sb.toString();
    }

    void checkSenderAmount(long senderId, TransactionDTO dto, BindingResult result) {
        BankAccount bankAccount = repository.findById(senderId);

        if (dto.getAmount() != null && (dto.getAmount() > bankAccount.getBalance())) {
            result.rejectValue(F_AMOUNT, C_AMOUNT_NOT_ENOUGH);
        }
    }
}
