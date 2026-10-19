package com.example.taketoday.catalog;

import com.example.taketoday.offer.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class CatalogService {
    private final OfferRepository offers;
    public CatalogService(OfferRepository offers) { this.offers = offers; }
    public List<Offer> find(CatalogFilter filter, Instant now) { return offers.findForCatalog(filter, now); }
    public Offer getPublic(UUID id) {
        return offers.getPublic(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Предложение не найдено"));
    }
}
