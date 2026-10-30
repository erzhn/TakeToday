package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Этап 4 (Эржан): выдача заказа (п. 4.7.5 ТЗ) — статус ISSUED, кто и когда выдал.
 *
 * <p>Миграция на Java, а не на SQL: проверку статусов в V6 Жаныбек объявил без имени, и PostgreSQL и H2 (тесты)
 * называют её по-разному. Здесь имя находим по информационной схеме — одинаково в обеих базах — и заменяем
 * проверку на именованную. Статус PICKUP_EXPIRED («Время получения истекло») сразу разрешён для этапа 5 Жаныбека.
 */
public class V10__pickup_issue extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection db = context.getConnection();
        for (String name : statusChecks(db)) {
            try (Statement st = db.createStatement()) {
                st.execute("ALTER TABLE reservation_orders DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
            }
        }
        try (Statement st = db.createStatement()) {
            st.execute("""
                    ALTER TABLE reservation_orders ADD CONSTRAINT reservation_status_check
                        CHECK (status IN ('AWAITING_PAYMENT', 'PAID', 'ISSUED', 'CANCELLED', 'PICKUP_EXPIRED'))""");
            st.execute("ALTER TABLE reservation_orders ADD COLUMN issued_at TIMESTAMP WITH TIME ZONE");
            st.execute("ALTER TABLE reservation_orders ADD COLUMN issued_by UUID REFERENCES accounts(id)");
            st.execute("CREATE INDEX reservation_status_offer ON reservation_orders(status, offer_id)");
        }
    }

    /** Проверки таблицы reservation_orders, в которых перечислены статусы заказа. */
    private static List<String> statusChecks(Connection db) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement q = db.prepareStatement("""
                SELECT cc.constraint_name, cc.check_clause
                FROM information_schema.check_constraints cc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name
                WHERE LOWER(tc.table_name) = 'reservation_orders'""");
             ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                String clause = rs.getString(2);
                if (clause != null && clause.contains("AWAITING_PAYMENT")) {
                    names.add(rs.getString(1));
                }
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("Не найдена проверка статусов reservation_orders (V6)");
        }
        return names;
    }
}
