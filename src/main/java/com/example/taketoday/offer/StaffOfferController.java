package com.example.taketoday.offer;

import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.CurrentUserService;
import com.example.taketoday.offer.dto.OfferForm;
import com.example.taketoday.venue.VenueService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Предложения в кабинете заведения (экран 8, п. 4.4 ТЗ).
 * Список предложений — на главной кабинета (/staff), здесь — создание, изменение и снятие с продажи.
 * Доступ к /staff/** только у роли STAFF (SecurityConfig), «только своё» проверяет OfferService.
 */
@Controller
@RequestMapping("/staff/offers")
public class StaffOfferController {

    private final OfferService offerService;
    private final VenueService venueService;
    private final CurrentUserService currentUser;

    public StaffOfferController(OfferService offerService, VenueService venueService, CurrentUserService currentUser) {
        this.offerService = offerService;
        this.venueService = venueService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list() {
        return "redirect:/staff#offers";
    }

    // ---------- Создание ----------

    @GetMapping("/new")
    public String newForm(@RequestParam(name = "from", required = false) UUID from, Model model,
                          RedirectAttributes redirect) {
        CurrentAccount staff = currentUser.require();
        if (!venueService.getSummaryForStaff(staff).isProfileComplete()) {
            redirect.addFlashAttribute("error", "Сначала заполните адрес заведения — без него нельзя выставлять предложения.");
            return "redirect:/staff/venue";
        }
        model.addAttribute("offerForm", from != null ? offerService.repeatForm(staff, from) : offerService.newForm());
        addFormData(model, staff, null, from);
        return "staff/offer-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("offerForm") OfferForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        CurrentAccount staff = currentUser.require();
        boolean canCopyPhoto = offerService.photoOfOwnOffer(staff, form.getCopyFrom()).isPresent();
        offerService.validate(form, errors, null, canCopyPhoto);
        if (errors.hasErrors()) {
            addFormData(model, staff, null, form.getCopyFrom());
            return "staff/offer-form";
        }
        offerService.create(staff, form);
        redirect.addFlashAttribute("success", "Предложение опубликовано");
        redirect.addFlashAttribute("successText", "Набор «" + form.getTitle().trim() + "» уже виден покупателям в каталоге.");
        return "redirect:/staff#offers";
    }

    // ---------- Изменение ----------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        CurrentAccount staff = currentUser.require();
        StaffOffer offer = offerService.getForStaff(staff, id);
        model.addAttribute("offerForm", offerService.toForm(offer));
        addFormData(model, staff, offer, null);
        return "staff/offer-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("offerForm") OfferForm form,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        CurrentAccount staff = currentUser.require();
        StaffOffer offer = offerService.getForStaff(staff, id);
        offerService.validate(form, errors, offer, false);
        if (!errors.hasErrors()) {
            try {
                offerService.update(staff, id, form);
                redirect.addFlashAttribute("success", "Изменения сохранены");
                redirect.addFlashAttribute("successText", "Покупатели уже видят обновлённый набор.");
                return "redirect:/staff#offers";
            } catch (OfferChangedException changed) {
                offer = offerService.getForStaff(staff, id);
                model.addAttribute("error", changed.getMessage());
            }
        }
        addFormData(model, staff, offer, null);
        return "staff/offer-form";
    }

    // ---------- Снять с продажи / вернуть ----------

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable UUID id, RedirectAttributes redirect) {
        if (offerService.withdraw(currentUser.require(), id)) {
            redirect.addFlashAttribute("success", "Предложение снято с продажи");
            redirect.addFlashAttribute("successText", "Новые брони невозможны. Уже оплаченные заказы нужно выдать как обычно.");
        } else {
            redirect.addFlashAttribute("error", "Это предложение уже не продаётся.");
        }
        return "redirect:/staff#offers";
    }

    @PostMapping("/{id}/resume")
    public String resume(@PathVariable UUID id, RedirectAttributes redirect) {
        if (offerService.resume(currentUser.require(), id)) {
            redirect.addFlashAttribute("success", "Предложение снова в продаже");
            redirect.addFlashAttribute("successText", "Покупатели снова могут его бронировать.");
        } else {
            redirect.addFlashAttribute("error", "Вернуть в продажу нельзя: время выдачи уже прошло. Измените время в предложении.");
        }
        return "redirect:/staff#offers";
    }

    private void addFormData(Model model, CurrentAccount staff, StaffOffer offer, UUID copyFrom) {
        model.addAttribute("foodTypes", FoodType.values());
        model.addAttribute("today", java.time.LocalDate.now(com.example.taketoday.common.AppTime.ZONE));
        model.addAttribute("venue", venueService.getSummaryForStaff(staff));
        model.addAttribute("offer", offer);
        model.addAttribute("editing", offer != null);
        String photoPath = offer != null ? offer.photoPath()
                : offerService.photoOfOwnOffer(staff, copyFrom).orElse(null);
        model.addAttribute("photoPath", photoPath);
    }
}
