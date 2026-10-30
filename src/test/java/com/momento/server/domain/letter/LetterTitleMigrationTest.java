package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LetterTitleMigrationTest {
  @Test
  void migrationPreservesLegacyDraftAndSubmittedLetters() {
    var source =
        new DriverManagerDataSource(
            "jdbc:h2:mem:letter-migration-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    try {
      Flyway.configure().dataSource(source).target("8").load().migrate();
      JdbcTemplate jdbc = new JdbcTemplate(source);
      jdbc.update(
          """
          INSERT INTO users (id, kakao_id, nickname, created_at, updated_at)
          VALUES (1, 'migration-user', '작성자', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      jdbc.update(
          """
          INSERT INTO time_capsules
            (id, creator_id, title, icon_type, capsule_type, visibility_type,
             open_at, letter_deadline_at, created_at, updated_at)
          VALUES (1, 1, '캡슐', 'ICE_CREAM', 'GROUP', 'ALL_MEMBERS',
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      Timestamp submittedAt = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 12, 0));
      String legacyContent = "가".repeat(10000);
      jdbc.update(
          """
          INSERT INTO letters
            (id, time_capsule_id, author_id, content, status, created_at, updated_at)
          VALUES (1, 1, 1, ?, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          legacyContent);
      jdbc.update(
          """
          INSERT INTO letters
            (id, time_capsule_id, author_id, content, theme_type, status, submitted_at,
             created_at, updated_at)
          VALUES (2, 1, 1, '제출 편지', 'COZY', 'SUBMITTED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          submittedAt);
      var before = jdbc.queryForList("SELECT * FROM letters ORDER BY id");

      Flyway.configure().dataSource(source).load().migrate();

      var after = jdbc.queryForList("SELECT * FROM letters ORDER BY id");
      assertThat(after).hasSize(2);
      for (int i = 0; i < after.size(); i++) {
        assertThat(after.get(i)).containsEntry("TITLE", null);
        after.get(i).remove("TITLE");
        assertThat(after.get(i)).isEqualTo(before.get(i));
      }
    } finally {
      new JdbcTemplate(source).execute("SHUTDOWN");
    }
  }
}
