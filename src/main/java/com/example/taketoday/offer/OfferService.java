package com.example.taketoday.offer;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.FileStorageService;
import com.example.taketoday.offer.dto.OfferForm;
import com.example.taketoday.venue.Establishment;
import com.example.taketoday.venue.VenueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Предложения заведения: создание, изменение, снятие с продажи (п. 4.4 ТЗ).
 *
 * Правило безопасности: предложение всегда ищется вместе с заведением вошедшего сотрудника,
 * поэтому чужое нельзя ни открыть, ни изменить, даже подставив его id в адрес (п. 5.2.3 ТЗ).
 */
@Service
public class OfferService {

    static final long PHOTO_MAX_BYTES = 5L * 1024 * 1024;
    private static final String PHOTO_FOLDER = "offers";
    private static final String UPLOADS = "/uploads/";

    private final StaffOfferRepository offers;
    private final VenueService venues;
    private final FileStorageService storage;

    public OfferService(StaffOfferRepository offers, VenueService venues, FileStorageService storage) {
        this.offers = offers;
        this.venues = venues;
        this.storage = storage;
    }

    // ---------- Для кабинета заведения ----------

    /** Предложения своего заведения для таблицы: сначала те, что на витрине, потом снятые и прошедшие. */
    @Transactional(readOnly = true)
    public List<OfferRow> listForStaff(CurrentAccount staff) {
        Establishment venue = venues.getForStaff(staff);
        Instant now = Instant.now();
        return offers.findByEstablishment(venue.id()).stream()
                .map(o -> OfferRow.of(o, now))
                .sorted(Comparator.comparingInt((OfferRow r) -> r.getState().ordinal()))
                .toList();
    }

    /** Своё предложение или 404. */
    @Transactional(readOnly = true)
    public StaffOffer getForStaff(CurrentAccount staff, UUID offerId) {
        Establishment venue = venues.getForStaff(staff);
        return offers.findByIdAndEstablishment(offerId, venue.id()).orElseThrow(OfferNotFoundException::new);
    }

    /** Пустая форма: выдача сегодня 19:00–21:00, 5 порций. */
    public OfferForm newForm() {
        OfferForm form = new OfferForm();
        form.setPickupDate(LocalDate.now(AppTime.ZONE));
        form.setPickupStart(LocalTime.of(19, 0));
        form.setPickupEnd(LocalTime.of(21, 0));
        form.setTotalPortions(5);
        return form;
    }

    /** «Повторить» прошедшее предложение: те же данные, выдача сегодня в то же время. */
    @Transactional(readOnly = true)
    public OfferForm repeatForm(CurrentAccount staff, UUID offerId) {
        StaffOffer source = getForStaff(staff, offerId);
        OfferForm form = toForm(source);
        form.setPickupDate(LocalDate.now(AppTime.ZONE));
        form.setCopyFrom(source.id());
        return form;
    }

    public OfferForm toForm(StaffOffer offer) {
        ZonedDateTime start = offer.pickupStart().atZone(AppTime.ZONE);
        OfferForm form = new OfferForm();
        form.setTitle(offer.title());
        form.setFoodType(offer.foodType());
        form.setComposition(offer.composition());
        form.setRegularPrice(offer.regularPrice());
        form.setDiscountPrice(offer.discountPrice());
        form.setTotalPortions(offer.totalPortions());
        form.setPickupDate(start.toLocalDate());
        form.setPickupStart(start.toLocalTime());
        form.setPickupEnd(offer.pickupEnd().atZone(AppTime.ZONE).toLocalTime());
        return form;
    }

    /**
     * Проверки, которые нельзя описать аннотациями (SPEC раздел 14).
     *
     * @param existing изменяемое предложение или null при создании
     * @param copyPhotoAvailable при «Повторить» фото можно не загружать — возьмём из прошлого предложения
     */
    public void validate(OfferForm form, Errors errors, StaffOffer existing, boolean copyPhotoAvailable) {
        if (form.getRegularPrice() != null && form.getDiscountPrice() != null
                && !errors.hasFieldErrors("discountPrice")
                && form.getDiscountPrice() >= form.getRegularPrice()) {
            errors.rejectValue("discountPrice", "price", "Цена со скидкой должна быть меньше обычной");
        }

        Integer portions = form.getTotalPortions();
        if (portions != null && !errors.hasFieldErrors("totalPortions")) {
            if (portions < 0) {
                errors.rejectValue("totalPortions", "portions", "Количество порций не может быть отрицательным");
            } else if (portions == 0) {
                errors.rejectValue("totalPortions", "portions", "Укажите хотя бы 1 порцию");
            } else if (existing != null && portions < existing.lockedPortions()) {
                errors.rejectValue("totalPortions", "portions",
                        "Нельзя сделать порций меньше, чем уже забронировано и оплачено (" + existing.lockedPortions() + ")");
            }
        }

        if (form.getPickupDate() != null && form.getPickupStart() != null && form.getPickupEnd() != null
                && !errors.hasFieldErrors("pickupEnd")) {
            if (!form.getPickupEnd().isAfter(form.getPickupStart())) {
                errors.rejectValue("pickupEnd", "time", "Время окончания выдачи раньше времени начала");
            } else if (!end(form).isAfter(Instant.now())) {
                errors.rejectValue("pickupEnd", "time", "Время окончания выдачи уже прошло");
            }
        }

        if (form.hasNewPhoto()) {
            String photoError = storage.validateImage(form.getPhoto(), PHOTO_MAX_BYTES);
            if (photoError != null) {
                errors.rejectValue("photo", "photo", photoError);
            }
        } else if (existing == null && !copyPhotoAvailable) {
            errors.rejectValue("photo", "photo", "Добавьте фото");
        }
    }

    /** Есть ли у предложения-образца фото, которое можно скопировать (для «Повторить»). */
    @Transactional(readOnly = true)
    public Optional<String> photoOfOwnOffer(CurrentAccount staff, UUID offerId) {
        if (offerId == null) {
            return Optional.empty();
        }
        Establishment venue = venues.getForStaff(staff);
        return offers.findByIdAndEstablishment(offerId, venue.id()).map(StaffOffer::photoPath);
    }

    /** Создаёт предложение своего заведения. Форма уже проверена. Возвращает id. */
    @Transactional
    public UUID create(CurrentAccount staff, OfferForm form) {
        Establishment venue = venues.getForStaff(staff);
        if (!venue.isProfileComplete()) {
            throw new IllegalStateException("Сначала заполните адрес заведения");
        }

        // «Повторить» без нового фото — берём то же фото (файл не удалится, пока на него ссылается хоть одно предложение)
        String photo = form.hasNewPhoto()
                ? savePhoto(form)
                : photoOfOwnOffer(staff, form.getCopyFrom()).orElseThrow(() -> new IllegalStateException("Добавьте фото"));

        Instant now = Instant.now();
        StaffOffer offer = new StaffOffer(UUID.randomUUID(), venue.id(), form.getTitle().trim(), form.getComposition().trim(),
                form.getFoodType(), photo, form.getRegularPrice(), form.getDiscountPrice(), form.getTotalPortions(),
                0, 0, start(form), end(form), OfferStatus.ACTIVE, now, now, 0);
        offers.insert(offer);
        return offer.id();
    }

    /**
     * Сохраняет изменения своего предложения. Форма уже проверена.
     *
     * @throws OfferChangedException если, пока форма была открыта, покупатели забронировали
     *                               столько порций, что новое количество стало меньше
     */
    @Transactional
    public void update(CurrentAccount staff, UUID offerId, OfferForm form) {
        StaffOffer current = getForStaff(staff, offerId);

        String newPhoto = form.hasNewPhoto() ? savePhoto(form) : null;
        StaffOffer changed = new StaffOffer(current.id(), current.establishmentId(), form.getTitle().trim(),
                form.getComposition().trim(), form.getFoodType(), newPhoto != null ? newPhoto : current.photoPath(),
                form.getRegularPrice(), form.getDiscountPrice(), form.getTotalPortions(),
                current.reservedPortions(), current.soldPortions(), start(form), end(form), current.status(),
                current.createdAt(), Instant.now(), current.version());

        if (!offers.updateByStaff(changed)) {
            deletePhotoIfUnused(newPhoto);
            StaffOffer fresh = offers.findById(offerId).orElseThrow(OfferNotFoundException::new);
            throw new OfferChangedException("Пока вы редактировали, покупатели забронировали порции. Сейчас забронировано и оплачено: "
                    + fresh.lockedPortions() + ". Проверьте количество и сохраните ещё раз.");
        }
        if (newPhoto != null) {
            deletePhotoIfUnused(current.photoPath());
        }
    }

    /** Снять с продажи (п. 4.4.3 ТЗ). Оплаченные заказы остаются в силе. */
    @Transactional
    public boolean withdraw(CurrentAccount staff, UUID offerId) {
        StaffOffer offer = getForStaff(staff, offerId);
        return offers.changeStatus(offer.id(), offer.establishmentId(), OfferStatus.ACTIVE, OfferStatus.WITHDRAWN);
    }

    /** Вернуть снятое предложение в продажу. Если время выдачи прошло — нельзя, нужно изменить время. */
    @Transactional
    public boolean resume(CurrentAccount staff, UUID offerId) {
        StaffOffer offer = getForStaff(staff, offerId);
        if (offer.isExpired(Instant.now())) {
            return false;
        }
        return offers.changeStatus(offer.id(), offer.establishmentId(), OfferStatus.WITHDRAWN, OfferStatus.ACTIVE);
    }

    // ---------- Фото ----------
    // В offers.photo_path хранится адрес картинки целиком, как ждёт каталог Жаныбека:
    // «/uploads/offers/<uuid>.jpg» — загруженные сотрудниками, «/images/offers/*.jpg» — демо-фото в проекте.

    private String savePhoto(OfferForm form) {
        return UPLOADS + storage.saveImage(form.getPhoto(), PHOTO_FOLDER);
    }

    /** Удаляет загруженный файл, если на него больше не ссылается ни одно предложение. Демо-фото не трогает. */
    private void deletePhotoIfUnused(String photoPath) {
        if (photoPath != null && photoPath.startsWith(UPLOADS) && offers.countByPhoto(photoPath) == 0) {
            storage.delete(photoPath.substring(UPLOADS.length()));
        }
    }

    private static Instant start(OfferForm form) {
        return form.getPickupDate().atTime(form.getPickupStart()).atZone(AppTime.ZONE).toInstant();
    }

    private static Instant end(OfferForm form) {
        return form.getPickupDate().atTime(form.getPickupEnd()).atZone(AppTime.ZONE).toInstant();
    }
}
