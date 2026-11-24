package com.lee.spa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicitly opted-in local MySQL checks. No Spring, Redis, DDL, commits, updates or deletes.
 * All fixture INSERTs use negative IDs and are rolled back, including on assertion failure.
 * Run: SPA_LOCAL_DB_TESTS=true MYSQL_PWD=<local password> mvn -Dtest=LocalDatabaseIntegrityTest test
 */
@EnabledIfEnvironmentVariable(named = "SPA_LOCAL_DB_TESTS", matches = "true")
class LocalDatabaseIntegrityTest {
    private Connection connect() throws SQLException {
        String database = System.getenv("SPA_ACCEPTANCE_DB");
        if(database == null || !database.matches("spa_acceptance_[0-9]{14}")) throw new IllegalStateException("Isolated acceptance database required");
        String url = "jdbc:mysql://127.0.0.1:3306/" + database;
        String password = System.getenv("SPA_ACCEPTANCE_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("MYSQL_PWD must be set explicitly for local database tests");
        }
        Connection connection = DriverManager.getConnection(
                url + "?serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=10000", System.getenv("SPA_ACCEPTANCE_DB_USER"), password);
        try {
            assertEquals(database, scalar(connection, "SELECT DATABASE()"));
            assertEquals("8", scalar(connection, "SELECT SUBSTRING_INDEX(VERSION(), '.', 1)"));
            connection.setAutoCommit(false);
            return connection;
        } catch (Throwable error) {
            connection.close();
            throw error;
        }
    }

    @Test
    void existingScheduleSchemaProtectsHistoricalRowsAndUniqueDates() throws Exception {
        try (Connection connection = connect()) {
            try {
                assertEquals("MANUAL", scalar(connection, "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='staff_schedule' AND COLUMN_NAME='source'"));
                assertEquals("NO", scalar(connection, "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='staff_schedule' AND COLUMN_NAME='source'"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='staff_schedule' "
                        + "AND COLUMN_NAME IN ('start_time','end_time') AND IS_NULLABLE='YES'"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='staff_schedule' "
                        + "AND COLUMN_NAME IN ('break_start_time','break_end_time')"));
                assertUnique(connection, "staff_schedule", "staff_id,work_date");
                assertUnique(connection, "staff_weekly_rule", "staff_id,day_of_week");
                assertUnique(connection, "appointment", "request_id");
                assertUnique(connection, "appointment_slot", "staff_id,slot_time");
                assertEquals(4, count(connection, "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('staff_schedule', 'staff_weekly_rule', "
                        + "'appointment','appointment_slot') AND ENGINE='InnoDB'"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void duplicateScheduleIsRejectedAndRestDayFixtureRollsBack() throws Exception {
        long id = isolatedId();
        try (Connection connection = connect()) {
            try {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM staff_schedule WHERE id IN (?,?) OR staff_id=?", id, id - 1, id));
            try {
                execute(connection, "INSERT INTO staff_schedule (id,staff_id,work_date,status) VALUES (?,?,'2099-01-05',0)", id, id);
                assertEquals("MANUAL", scalar(connection, "SELECT source FROM staff_schedule WHERE id=?", id));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM staff_schedule WHERE id=? AND start_time IS NULL AND end_time IS NULL", id));
                SQLException duplicate = assertThrows(SQLException.class, () -> execute(connection,
                        "INSERT INTO staff_schedule (id,staff_id,work_date,status) VALUES (?,?,'2099-01-05',0)", id - 1, id));
                assertDuplicate(duplicate);
            } finally {
                connection.rollback();
            }
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM staff_schedule WHERE id IN (?,?) OR staff_id=?", id, id - 1, id));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void duplicateSlotsCannotLeavePartialAppointmentAfterRollback() throws Exception {
        long id = isolatedId();
        try (Connection connection = connect()) {
            try {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM appointment WHERE id=? OR staff_id=?", id, id));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM appointment_slot WHERE id IN (?,?) OR staff_id=?", id, id - 1, id));
            try {
                execute(connection, "INSERT INTO appointment (id,appointment_no,user_id,service_id,staff_id,appointment_date,"
                        + "start_time,end_time,amount,status,request_id) VALUES (?,?,?,?,?,'2099-01-05','10:00:00','10:45:00',1.00,1,?)",
                        id, "jdbc-test-" + (-id), id, id, id, "jdbc-test-" + (-id));
                execute(connection, "INSERT INTO appointment_slot (id,appointment_id,staff_id,slot_time) VALUES (?,?,?,'2099-01-05 10:00:00')", id, id, id);
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM appointment WHERE id=?", id));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM appointment_slot WHERE id=?", id));
                SQLException duplicate = assertThrows(SQLException.class, () -> execute(connection,
                        "INSERT INTO appointment_slot (id,appointment_id,staff_id,slot_time) VALUES (?,?,?,'2099-01-05 10:00:00')", id - 1, id, id));
                assertDuplicate(duplicate);
            } finally {
                connection.rollback();
            }
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM appointment WHERE id=? OR staff_id=?", id, id));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM appointment_slot WHERE id IN (?,?) OR staff_id=?", id, id - 1, id));
            } finally {
                connection.rollback();
            }
        }
    }

    private static long isolatedId() {
        // Explicit negative IDs do not advance the existing positive AUTO_INCREMENT sequences.
        return -ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_000_000_000_000L);
    }

    private static void assertDuplicate(SQLException error) {
        assertEquals("23000", error.getSQLState());
        assertEquals(1062, error.getErrorCode());
    }

    private static void assertUnique(Connection connection, String table, String columns) throws SQLException {
        assertEquals(1, count(connection, "SELECT COUNT(*) FROM (SELECT INDEX_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND NON_UNIQUE=0 GROUP BY INDEX_NAME "
                + "HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)=?) matching_indexes", table, columns));
    }

    private static String scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters); ResultSet result = statement.executeQuery()) {
            assertTrue(result.next(), "Expected a scalar database result");
            return result.getString(1);
        }
    }

    private static long count(Connection connection, String sql, Object... parameters) throws SQLException {
        return Long.parseLong(scalar(connection, sql, parameters));
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        } catch (Throwable error) {
            statement.close();
            throw error;
        }
    }
}
