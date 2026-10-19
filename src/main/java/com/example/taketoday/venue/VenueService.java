package com.example.taketoday.venue;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.FileStorageService;
import com.example.taketoday.common.ValidationRules;
import com.example.taketoday.venue.dto.VenueForm;
import com.example.taketoday.venue.dto.WorkDayForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.List;

/**
 * Сведения о заведении (п. 4.2.2, 4.2.3 ТЗ).
 *
 * Правило безопасности: сотрудник работает только со СВОИМ заведением —
 * оно ищется по owner_id = id вошедшего пользователя, а не по id из адреса или формы.
 */
@Service
public class VenueService {

    static final long LOGO_MAX_BYTES = 2L * 1024 * 1024;

    private final EstablishmentRepository establishments;
    private final FileStorageService storage;

    public VenueService(EstablishmentRepository establishments, FileStorageService storage) {
        this.establishments = establishments;
        this.storage = storage;
    }

    /** Заведение сотрудника. Если пользователь не сотрудник или заведения нет — 403. */
    @Transactional(readOnly = true)
    public Establishment getForStaff(CurrentAccount staff) {
        if (!staff.isStaff()) {
            throw new VenueAccessException("У этой учётной записи нет заведения");
        }
        return establishments.findByOwner(staff.getId())
                .orElseThrow(() -> new VenueAccessException("Заведение не найдено"));
    }

    @Transactional(readOnly = true)
    public VenueSummary getSummaryForStaff(CurrentAccount staff) {
        return VenueSummary.of(getForStaff(staff));
    }

    /** Данные заведения в виде формы — для страницы «Сведения о заведении». */
    public VenueForm toForm(Establishment e) {
        VenueForm form = new VenueForm();
        form.setName(e.name());
        form.setCategory(e.category());
        form.setDescription(e.description());
        form.setAddress(e.address());
        form.setDistrict(e.district());
        // При регистрации в «Контакт» можно указать телефон или почту (форма Жаныбека).
        // Почту переносим в поле почты, чтобы сотрудник не получил ошибку формата телефона.
        String contact = e.contact();
        boolean contactIsEmail = contact != null && contact.contains("@")
                && !contact.matches(ValidationRules.PHONE_REGEX);
        form.setPhone(contactIsEmail ? null : contact);
        form.setContactEmail(e.contactEmail() != null ? e.contactEmail() : (contactIsEmail ? contact : null));
        form.setInstagram(e.instagram());
        List<WorkDayForm> days = new ArrayList<>();
        for (WorkDayForm d : e.workDays()) {
            days.add(new WorkDayForm(d.getDayOfWeek(), d.isWorking(), d.getOpenTime(), d.getCloseTime()));
        }
        form.setWorkDays(days);
        return form;
    }

    /** «14 октября 2026, 16:45» или «ещё не сохранялись». */
    public String updatedLabel(Establishment e) {
        return AppTime.format(e.updatedAt());
    }

    /**
     * Проверки, которые нельзя описать аннотациями: график и логотип.
     * Ошибки кладутся под нужное поле, чтобы показать их рядом с ним.
     */
    public void validate(VenueForm form, Errors errors) {
        if (ValidationRules.trimToNull(form.getPhone()) == null
                && ValidationRules.trimToNull(form.getContactEmail()) == null
                && !errors.hasFieldErrors("phone")) {
            errors.rejectValue("phone", "contact", "Укажите телефон или почту для связи");
        }

        List<WorkDayForm> days = form.getWorkDays();
        if (days == null || days.size() != 7) {
            errors.rejectValue("workDays", "schedule", "Укажите график работы");
        } else {
            boolean anyWorking = false;
            for (int i = 0; i < days.size(); i++) {
                WorkDayForm day = days.get(i);
                if (day.getDayOfWeek() != i + 1) {
                    errors.rejectValue("workDays", "schedule", "Укажите график работы");
                    break;
                }
                if (!day.isWorking()) {
                    continue;
                }
                anyWorking = true;
                String path = "workDays[" + i + "]";
                if (day.getOpenTime() == null || day.getCloseTime() == null) {
                    errors.rejectValue(path + ".openTime", "schedule", "Укажите время открытия и закрытия");
                } else if (!day.getCloseTime().isAfter(day.getOpenTime())) {
                    errors.rejectValue(path + ".closeTime", "schedule", "Время закрытия раньше времени открытия");
                }
            }
            if (!anyWorking && !errors.hasFieldErrors("workDays")) {
                errors.rejectValue("workDays", "schedule", "Укажите хотя бы один рабочий день");
            }
        }

        String logoError = storage.validateImage(form.getLogo(), LOGO_MAX_BYTES);
        if (logoError != null) {
            errors.rejectValue("logo", "logo", logoError);
        }
    }

    /** Сохраняет сведения о заведении сотрудника. Форма уже проверена. */
    @Transactional
    public void updateForStaff(CurrentAccount staff, VenueForm form) {
        Establishment current = getForStaff(staff);

        String logoPath = current.logoPath();
        String oldLogoToDelete = null;
        if (form.getLogo() != null && !form.getLogo().isEmpty()) {
            oldLogoToDelete = logoPath;
            logoPath = storage.saveImage(form.getLogo(), "venues");
        } else if (form.isRemoveLogo()) {
            oldLogoToDelete = logoPath;
            logoPath = null;
        }

        Establishment updated = new Establishment(
                current.id(),
                current.ownerId(),
                form.getName().trim(),
                form.getCategory(),
                ValidationRules.trimToNull(form.getDescription()),
                form.getAddress().trim(),
                form.getDistrict(),
                contactFor(form),
                lowerOrNull(form.getContactEmail()),
                normalizeInstagram(form.getInstagram()),
                logoPath,
                ScheduleText.of(form.getWorkDays()),
                null,
                form.getWorkDays(),
                true);
        establishments.update(current.id(), updated);

        if (oldLogoToDelete != null) {
            storage.delete(oldLogoToDelete);
        }
    }

    /** Что записать в establishments.contact (обязательное поле): телефон, а если его нет — почту. */
    private static String contactFor(VenueForm form) {
        String phone = ValidationRules.normalizePhone(form.getPhone());
        return phone != null ? phone : lowerOrNull(form.getContactEmail());
    }

    private static String lowerOrNull(String value) {
        String trimmed = ValidationRules.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    /** «https://instagram.com/tandyr» и «tandyr» → «@tandyr». */
    static String normalizeInstagram(String value) {
        String trimmed = ValidationRules.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String handle = trimmed.replaceFirst("^(https?://)?(www\\.)?instagram\\.com/", "")
                .replaceAll("/+$", "")
                .replaceFirst("^@", "");
        return handle.isEmpty() ? null : "@" + handle;
    }
}
