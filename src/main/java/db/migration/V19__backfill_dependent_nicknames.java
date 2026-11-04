package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import plad.services.nickname.NicknameGenerator;
import plad.services.nickname.NicknameWordLists;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Gives a random nickname (same generator as the registration) to every dependent that has none yet,
// and opens their row in dependent_nickname_history.
public class V19__backfill_dependent_nicknames extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        backfill(context.getConnection(), new NicknameGenerator(NicknameWordLists.load(), new SecureRandom()));
    }

    // split from migrate() so a test can run it with a seeded generator
    public static int backfill(Connection connection, NicknameGenerator generator) throws SQLException {
        Set<String> taken = new HashSet<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT nickname FROM dependents WHERE nickname IS NOT NULL");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                taken.add(rs.getString(1));
            }
        }

        List<Long> pending = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT user_id FROM dependents WHERE nickname IS NULL ORDER BY user_id");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                pending.add(rs.getLong(1));
            }
        }

        try (PreparedStatement update = connection.prepareStatement("UPDATE dependents SET nickname = ? WHERE user_id = ?");
             PreparedStatement history = connection.prepareStatement(
                     "INSERT INTO dependent_nickname_history (dependent_id, nickname) VALUES (?, ?)")) {
            for (Long dependentId : pending) {
                String nickname = generator.claim(taken::add);
                update.setString(1, nickname);
                update.setLong(2, dependentId);
                update.executeUpdate();
                history.setLong(1, dependentId);
                history.setString(2, nickname);
                history.executeUpdate();
            }
        }
        return pending.size();
    }
}
