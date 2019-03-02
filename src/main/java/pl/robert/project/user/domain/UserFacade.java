package pl.robert.project.user.domain;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import pl.robert.project.bank.account.BankAccountFacade;
import pl.robert.project.user.domain.dto.*;
import pl.robert.project.user.query.UserQuery;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserFacade {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    UserRepository repository;
    UserValidation validation;
    PasswordEncoder passwordEncoder;
    BankAccountFacade bankAccountFacade;
    ConfirmationTokenRepository tokenRepository;
    JavaMailSender mailSender;

    public void generateBankAccount(CreateUserDTO dto) {
        dto.setPhoneNumber(formatPhoneNumber(dto.getPhoneNumber()));
        dto.setBankAccount(bankAccountFacade.create());
    }

    public void generateEmailConfirmationToken(CreateUserDTO dto) {
        User user = UserFactory.create(dto);
        repository.save(user);

        ConfirmationToken confirmationToken = new ConfirmationToken(user);
        tokenRepository.save(confirmationToken);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            String htmlMsg = "To confirm your account, please follow the link below:<br>" +
                    "<a href='http://localhost:8080/confirm-account?token=" + confirmationToken.getConfirmationToken() + "'>" +
                    "http://localhost:8080/confirm-account?token=" + confirmationToken.getConfirmationToken() + "</a>";
            mimeMessage.setContent(htmlMsg, "text/html");
            helper.setTo(user.getEmail());
            helper.setSubject("Complete Registration!");
            helper.setFrom("Rob");
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailSendException e) {
            tokenRepository.delete(confirmationToken);
            logger.info("Deleting token cause {} appeared...", e.getClass());
            throw new MailSendException("MailSendException | MessagingException");
        }
    }

    public void generateResetToken(ForgotLoginOrPasswordDTO dto) {
        User user = repository.findByEmail(dto.getForgottenEmail());
        ConfirmationToken confirmationToken = new ConfirmationToken(user);
        tokenRepository.save(confirmationToken);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            String htmlMsg = "Thank you for your password reset request. Your login is: <b>" + user.getLogin() + "</b><br>" +
                    "Please follow the link below to reset your password:<br>" +
                    "<a href='http://localhost:8080/reset-password?token=" + confirmationToken.getConfirmationToken() + "'>" +
                    "http://localhost:8080/reset-password?token=" + confirmationToken.getConfirmationToken() + "</a>";
            mimeMessage.setContent(htmlMsg, "text/html");
            helper.setTo(user.getEmail());
            helper.setSubject("Forgotten Password!");
            helper.setFrom("Rob");
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailSendException e) {
            tokenRepository.delete(confirmationToken);
            logger.info("Deleted token cause {} appeared...", e.getClass());
            throw new MailSendException("MailSendException | MessagingException");
        }
    }

    public boolean checkConfirmationToken(int choice, String confirmationToken) {
        ConfirmationToken token = tokenRepository.findByConfirmationToken(confirmationToken);
        User user = repository.findByEmail(token.getUser().getEmail());
        if (getNumberOfTokens() > 1) {
            for (long i=1L; i<getNumberOfTokens(); ++i) {
                ConfirmationToken tokenToCheck = tokenRepository.findById(i);
                if (token != null && (getCurrentTimeInSeconds() - Long.parseLong(tokenToCheck.getCreatedDateInSeconds())) > 900) {
                    tokenRepository.delete(tokenToCheck);
                }
            }
        } else {
            if ((getCurrentTimeInSeconds() - Long.parseLong(token.getCreatedDateInSeconds())) > 900) {
                tokenRepository.delete(token);
            }
        }
        token = tokenRepository.findByConfirmationToken(confirmationToken);
        // Register account
        if (choice == 1) {
            if (token != null) {
                repository.findUserByEmailAndUpdateEnabled(user.getEmail());
                tokenRepository.delete(token);
                logger.info("Email confirmation correct");
                return true;
            }
            logger.warn("Token expired! User email = {} is deleting now", user.getEmail());
            repository.delete(user);
            return false;
        }
        // Reset account
        return true;
    }

    private int getNumberOfTokens() {
        return Ints.checkedCast(tokenRepository.findFirstByOrderByIdDesc().getId());
    }

    private long getCurrentTimeInSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis()));
    }

    public boolean checkIfConfirmationTokenAlreadySent(ForgotLoginOrPasswordDTO dto) {
        User user = repository.findByEmail(dto.getForgottenEmail());

        if (user == null) return false;

        return tokenRepository.findByUser(user) != null;
    }

    public void resetPassword(String confirmationToken, String newPassword) {
        ConfirmationToken token = tokenRepository.findByConfirmationToken(confirmationToken);
        User user = repository.findByEmail(token.getUser().getEmail());
        changePassword(user.getId(), newPassword);
        tokenRepository.delete(token);
        logger.info("User password has been changed successfully and token has been deleted");
    }

    private String formatPhoneNumber(String phoneNumber) {
        StringBuilder sb = new StringBuilder(15);
        int temp = 0;
        sb.append("+48 ");
        for (char c : phoneNumber.toCharArray()) {
            sb.append(c);
            if (temp == 2 || temp == 5) sb.append('-');
            temp++;
        }
        return sb.toString();
    }

    public boolean isLoginUnique(String login) {
        return repository.findByLogin(login) == null;
    }

    public boolean isEmailUnique(String email) {
        return repository.findByEmail(email) == null;
    }

    public boolean isPhoneUnique(String phone) {
        return repository.findByPhoneNumber(formatPhoneNumber(phone)) == null;
    }

    public boolean isLoginExists(String login) {
        return repository.findByLogin(login) != null;
    }

    public boolean isLoginAndPasswordCorrect(String login, String password) {
        return repository.findByLoginAndPassword(login, password) != null;
    }

    public AuthorizationDTO findByLogin(String login) throws NullPointerException {
        User user = repository.findByLogin(login);

        if (user == null) return null;

        return new AuthorizationDTO(user.getLogin(), user.getPassword(), user.isEnabled(), user.getRoles());
    }

    public UserQuery QueryByLogin(String login) throws NullPointerException {
        User user = repository.findByLogin(login);

        if (user == null) return null;

        user.setBankAccount(bankAccountFacade.findById(user.getId()));

        return UserFactory.create(user);
    }

    public ImmutableMap<String, String> initializeMapWithUserDetails(UserQuery userQuery) {
        return ImmutableMap.<String, String>builder()
                .put("id", String.valueOf(userQuery.getId()))
                .put("login", userQuery.getLogin())
                .put("email", userQuery.getEmail())
                .put("phoneNumber", userQuery.getPhoneNumber())
                .put("bankAccountNumber", userQuery.getBankAccountNumber())
                .put("balance", String.valueOf(userQuery.getBalance()))
                .build();
    }

    public long findIdByLogin(String login) {
        return repository.findByLogin(login).getId();
    }

    public Page<User> findAll(int page, int size) {
        return repository.findAll(new PageRequest(page, size));
    }

    public void deleteById(long id) {
        repository.deleteById(id);
    }

    public void changeEmail(long userId, String newEmail) {
        repository.findUserByIdAndUpdateEmail(userId, newEmail);
    }

    public void checkConfirmedEmail(ChangeEmailDTO dto, BindingResult result) {
        validation.checkConfirmedEmail(dto, result);
    }

    public void changePhoneNumber(long userId, String newPhoneNumber) {
        repository.findUserByIdAndUpdatePhoneNumber(userId, formatPhoneNumber(newPhoneNumber));
    }

    public void checkConfirmedPhoneNumber(ChangePhoneNumberDTO dto, BindingResult result) {
        validation.checkConfirmedPhoneNumber(dto, result);
    }

    public void changePassword(long userId, String newPassword) {
        repository.findUserByIdAndUpdatePassword(userId, passwordEncoder.encode(newPassword));
    }

    public void checkConfirmedPassword(Object obj, BindingResult result) {
        validation.checkConfirmedPassword(obj, result);
    }

    public void checkForgottenEmail(boolean tokenAlreadySent, ForgotLoginOrPasswordDTO dto, BindingResult result) {
        validation.checkForgottenEmail(tokenAlreadySent, dto, result);
    }
}
