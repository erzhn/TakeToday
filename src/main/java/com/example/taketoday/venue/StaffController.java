package com.example.taketoday.venue;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.CurrentUserService;
import com.example.taketoday.offer.OfferRow;
import com.example.taketoday.offer.OfferService;
import com.example.taketoday.venue.dto.VenueForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Кабинет сотрудника заведения (экран 7) и «Сведения о заведении» (экран 13, п. 4.2.2 ТЗ).
 * Доступ к /staff/** только у роли STAFF — это проверяет SecurityConfig.
 */
@Controller
@RequestMapping("/staff")
public class StaffController {

    private final VenueService venueService;
    private final OfferService offerService;
    private final CurrentUserService currentUser;

    public StaffController(VenueService venueService, OfferService offerService, CurrentUserService currentUser) {
        this.venueService = venueService;
        this.offerService = offerService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String home(Model model) {
        CurrentAccount staff = currentUser.require();
        List<OfferRow> offers = offerService.listForStaff(staff);
        model.addAttribute("venue", venueService.getSummaryForStaff(staff));
        model.addAttribute("today", AppTime.today());
        model.addAttribute("offers", offers);
        model.addAttribute("liveCount", offers.stream().filter(OfferRow::isLive).count());
        model.addAttribute("offCount", offers.stream().filter(OfferRow::isOff).count());
        model.addAttribute("expiredCount", offers.stream().filter(OfferRow::isExpired).count());
        model.addAttribute("onSaleCount", offers.stream().filter(r -> r.getState() == OfferRow.State.ACTIVE).count());
        return "staff/home";
    }

    @GetMapping("/venue")
    public String venueForm(Model model) {
        Establishment venue = venueService.getForStaff(currentUser.require());
        model.addAttribute("venueForm", venueService.toForm(venue));
        addFormData(model, venue);
        return "staff/venue";
    }

    @PostMapping("/venue")
    public String saveVenue(@Valid @ModelAttribute("venueForm") VenueForm form,
                            BindingResult errors,
                            Model model,
                            RedirectAttributes redirect) {
        CurrentAccount staff = currentUser.require();
        Establishment venue = venueService.getForStaff(staff);
        venueService.validate(form, errors);

        if (errors.hasErrors()) {
            addFormData(model, venue);
            return "staff/venue";
        }

        venueService.updateForStaff(staff, form);
        redirect.addFlashAttribute("success", "Сведения сохранены");
        redirect.addFlashAttribute("successText",
                "Обновлённые данные и режим работы уже применены к карточке заведения.");
        return "redirect:/staff/venue";
    }

    private void addFormData(Model model, Establishment venue) {
        model.addAttribute("logoPath", venue.logoPath());
        model.addAttribute("registrationHours", venue.scheduleSaved() ? null : venue.workingHours());
        model.addAttribute("updatedLabel", venueService.updatedLabel(venue));
        model.addAttribute("categories", VenueCategory.values());
        model.addAttribute("districts", District.values());
    }
}
