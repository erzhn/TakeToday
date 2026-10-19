package com.example.taketoday.catalog;

import com.example.taketoday.offer.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@Controller
public class CatalogController {
    private final CatalogService offers;
    public CatalogController(CatalogService offers) { this.offers = offers; }

    @GetMapping("/catalog")
    public String catalog(@Valid @ModelAttribute("filter") CatalogFilter filter, BindingResult errors, Model model,
                          jakarta.servlet.http.HttpServletResponse response) {
        if (!errors.hasFieldErrors("minPrice") && !errors.hasFieldErrors("maxPrice")
                && filter.getMinPrice() != null && filter.getMaxPrice() != null && filter.getMinPrice() > filter.getMaxPrice())
            errors.rejectValue("maxPrice", "range", "Максимальная цена должна быть не меньше минимальной");
        if (!errors.hasFieldErrors("pickupAfter") && !errors.hasFieldErrors("pickupBefore")
                && filter.getPickupAfter() != null && filter.getPickupBefore() != null && filter.getPickupAfter().isAfter(filter.getPickupBefore()))
            errors.rejectValue("pickupBefore", "range", "Время «до» должно быть не раньше времени «с»");
        Instant now = Instant.now();
        List<Offer> rows = errors.hasErrors() ? List.of() : offers.find(filter, now);
        if (errors.hasErrors()) response.setStatus(400);
        model.addAttribute("types", FoodType.values());
        model.addAttribute("offers", rows.subList(0, Math.min(rows.size(), OfferRepository.PAGE_SIZE)));
        model.addAttribute("hasNext", rows.size() > OfferRepository.PAGE_SIZE);
        model.addAttribute("now", now);
        return "catalog/index";
    }

    @GetMapping("/offers/{id}")
    public String details(@PathVariable UUID id, Model model) {
        Offer offer = offers.getPublic(id);
        model.addAttribute("offer", offer);
        model.addAttribute("availability", offer.availabilityAt(Instant.now()));
        return "catalog/details";
    }
}
