package com.example.taketoday.profile;

import com.example.taketoday.auth.AuthModels;
import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.CurrentUserService;
import com.example.taketoday.profile.dto.PasswordForm;
import com.example.taketoday.profile.dto.ProfileForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Личный кабинет (экран 6 в Figma, п. 4.2.1 ТЗ). Открыт любому вошедшему пользователю.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserService currentUser;
    private final SecurityContextRepository contextRepository;

    public ProfileController(ProfileService profileService, CurrentUserService currentUser,
                             SecurityContextRepository contextRepository) {
        this.profileService = profileService;
        this.currentUser = currentUser;
        this.contextRepository = contextRepository;
    }

    @GetMapping
    public String view(Model model) {
        CurrentAccount account = currentUser.require();
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", profileService.toForm(account));
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        return "profile/index";
    }

    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form,
                                BindingResult errors,
                                Model model,
                                RedirectAttributes redirect) {
        CurrentAccount account = currentUser.require();
        if (errors.hasErrors()) {
            model.addAttribute("passwordForm", new PasswordForm());
            return "profile/index";
        }
        profileService.updateProfile(account.getId(), form);
        redirect.addFlashAttribute("success", "Данные сохранены");
        redirect.addFlashAttribute("successText", "Изменения успешно применены к вашему профилю.");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordForm form,
                                 BindingResult errors,
                                 Model model,
                                 RedirectAttributes redirect,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        CurrentAccount account = currentUser.require();
        profileService.validatePasswordChange(account.getId(), form, errors);
        if (errors.hasErrors()) {
            model.addAttribute("profileForm", profileService.toForm(account));
            return "profile/index";
        }

        int newVersion = profileService.changePassword(account.getId(), form);
        keepCurrentSession(newVersion, request, response);

        redirect.addFlashAttribute("success", "Пароль изменён");
        redirect.addFlashAttribute("successText", "На других устройствах нужно будет войти заново.");
        return "redirect:/profile#password";
    }

    /**
     * После смены пароля auth_version в базе вырос, и SessionGuard выкинул бы и эту сессию.
     * Кладём в текущую сессию пользователя с новой версией — остаётся вошедшим только этот браузер.
     */
    private void keepCurrentSession(int newVersion, HttpServletRequest request, HttpServletResponse response) {
        currentUser.principal().ifPresent(old -> {
            AuthModels.User updated = new AuthModels.User(old.id(), old.email(), old.name(), old.role(), newVersion);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, auth.getAuthorities()));
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
        });
    }
}
