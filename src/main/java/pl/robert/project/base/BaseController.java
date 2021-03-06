package pl.robert.project.base;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import pl.robert.project.user.domain.UserFacade;
import pl.robert.project.user.domain.dto.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@Controller
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class BaseController {

    UserFacade userFacade;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new CreateUserDTO());
        return "register";
    }

    @PostMapping("/register")
    public String registerForm(@Valid @ModelAttribute("user") CreateUserDTO dto, BindingResult result, Model model) {
        userFacade.checkConfirmedPassword(dto, result);
        if (result.hasErrors()) {
            model.addAttribute("user", dto);
            return "register";
        }
        userFacade.saveUserAndGenerateAccountConfirmationToken(dto);
        model.addAttribute("email", dto.getEmail());
        model.addAttribute("typeOfToken", "Account confirmation ");
        return "tokenSent";
    }

    @GetMapping("/confirm-account")
    public String confirmUserAccount(@RequestParam("token") String confirmationToken) {
        boolean isCorrect = userFacade.confirmAccountToken(confirmationToken);
        if (isCorrect) {
            return "accountVerificationSuccess";
        }
        return "accountVerificationFailed";
    }

    @GetMapping("/forgot-password")
    public String forgotLoginOrPassword(Model model) {
        model.addAttribute("DTO", new ForgotLoginOrPasswordDTO());
        return "forgotPassword";
    }

    @PostMapping("/forgot-password")
    public String forgotLoginOrPassword(@Valid @ModelAttribute("DTO") ForgotLoginOrPasswordDTO dto, BindingResult result, Model model) {
        userFacade.checkForgottenEmail(userFacade.checkIfTokenAlreadySent(dto), dto, result);
        if (result.hasErrors()) {
            model.addAttribute("DTO", dto);
            return "forgotPassword";
        }
        userFacade.generateAndSaveResetConfirmationToken(dto);
        model.addAttribute("email", dto.getForgottenEmail());
        model.addAttribute("typeOfToken", "Reset");
        return "tokenSent";
    }

    @GetMapping("/reset-password")
    public String resetPassword(Model model, @RequestParam("token") String confirmationToken) {
        boolean isCorrect = userFacade.confirmResetToken(confirmationToken);
        if (isCorrect) {
            model.addAttribute("DTO", new ChangePasswordDTO());
            model.addAttribute("token", confirmationToken);
            return "resetPassword";
        }
        return "resetPasswordFailed";
    }

    @PatchMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("DTO") ChangePasswordDTO dto, BindingResult result,
                                Model model, @RequestParam("token") String confirmationToken) {
        userFacade.checkConfirmedPassword(dto, result);
        if (result.hasErrors()) {
            model.addAttribute("flag", true);
            model.addAttribute("DTO", dto);
            model.addAttribute("token", confirmationToken);
            return "resetPassword";
        }
        userFacade.setNewPasswordAndDeleteToken(dto.getConfirmedPassword(), confirmationToken);
        return "resetPasswordCompleted";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("user", new AuthorizationDTO());
        return "login";
    }

    @PostMapping("/login")
    public String loginForm(@Valid @ModelAttribute("user") SignInDTO dto, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("user", dto);
            return "login";
        }
        return "redirect:/user-panel";
    }

    @GetMapping("/logout")
    public String logMeOut(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return "redirect:/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "accessDenied";
    }
}
