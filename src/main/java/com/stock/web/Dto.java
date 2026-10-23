package com.stock.web;

import com.stock.model.AppUser;
import com.stock.model.Category;
import com.stock.model.Part;
import com.stock.model.StockMovement;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Усі DTO в одному місці — те, що бачить фронтенд. */
public final class Dto {
    private Dto() {}

    public record CategoryView(Long id, String name, long partCount) {
        public static CategoryView of(Category c, long count) {
            return new CategoryView(c.getId(), c.getName(), count);
        }
    }

    public record PartView(Long id, Long categoryId, String name, int quantity, int minQuantity, boolean low) {
        public static PartView of(Part p) {
            boolean low = p.getMinQuantity() > 0 && p.getQuantity() <= p.getMinQuantity();
            return new PartView(p.getId(), p.getCategory().getId(), p.getName(),
                    p.getQuantity(), p.getMinQuantity(), low);
        }
    }

    public record MovementView(Long id, int delta, int quantityAfter, Instant createdAt, String note, String username) {
        public static MovementView of(StockMovement m) {
            return new MovementView(m.getId(), m.getDelta(), m.getQuantityAfter(), m.getCreatedAt(), m.getNote(), m.getUsername());
        }
    }

    public record UserView(Long id, String username, AppUser.Role role) {
        public static UserView of(AppUser u) { return new UserView(u.getId(), u.getUsername(), u.getRole()); }
    }

    /** Хто зараз увійшов. defaultPassword — адмін досі з паролем admin; login — чи увімкнено вхід. */
    public record Me(String username, boolean admin, boolean defaultPassword, boolean login) {}

    public record ImportResult(int categoriesCreated, int partsCreated, int partsUpdated, int rowsSkipped) {}

    public record RenameCategory(@NotBlank @Size(max = 100) String name) {}

    public record CreateCategory(@NotBlank @Size(max = 100) String name) {}

    public record CreatePart(@NotNull Long categoryId,
                             @NotBlank @Size(max = 150) String name,
                             @Min(0) int quantity,
                             @Min(0) int minQuantity) {}

    public record UpdatePart(@NotBlank @Size(max = 150) String name, @Min(0) int minQuantity) {}

    public record Adjust(int delta, @Size(max = 255) String note) {}

    public record Login(@NotBlank String username, @NotBlank String password) {}

    public record CreateUser(@NotBlank @Size(max = 50) String username,
                             @NotBlank @Size(min = 4, max = 100) String password,
                             @NotNull AppUser.Role role) {}

    /** password порожній — не змінювати; role null — не змінювати. */
    public record UpdateUser(@Size(max = 100) String password, AppUser.Role role) {}

    public record ChangePassword(@NotBlank @Size(min = 4, max = 100) String password) {}

    public record ImportLink(@NotBlank @Size(max = 500) String url) {}
}
