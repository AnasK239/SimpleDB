package Tests;

import KV.Comparison;
import Relational.DB;
import Relational.DBRecord;
import Relational.DBTX;
import Relational.Scanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TransactionsTest {
    public static void main(String[] args) throws Exception {

        try (DB db = new DB(Path.of("tables-ch10.db"))) {
            db.open();

            try (DBTX tx = db.begin()) {
                tx.update(
                        "users",
                        new DBRecord()
                                .addInt64("id", 7)
                                .addStr(
                                        "name",
                                        "Anas".getBytes(StandardCharsets.UTF_8)
                                )
                                .addInt64("age", 26)
                );

                tx.commit();
            }

            try (DBTX tx = db.begin()) {
                tx.insert(
                        "users",
                        new DBRecord()
                                .addInt64("id", 40)
                                .addStr(
                                        "name",
                                        "Mona".getBytes(StandardCharsets.UTF_8)
                                )
                                .addInt64("age", 28)
                );

                tx.delete(
                        "users",
                        new DBRecord().addInt64("id", 19)
                );

                tx.commit();
            }

            try (DBTX tx = db.begin()) {
                tx.delete(
                        "users",
                        new DBRecord().addInt64("id", 7)
                );

                tx.abort();
            }

            try (DBTX tx = db.begin()) {
                tx.delete(
                        "users",
                        new DBRecord().addInt64("id", 7)
                );

                // No commit shouldn't delete
            }

            try(DBTX tx = db.begin()) {
                tx.insert(
                        "users",
                        new DBRecord()
                                .addInt64("id", 100)
                                .addStr(
                                        "name",
                                        "NEW".getBytes(StandardCharsets.UTF_8)
                                )
                                .addInt64("age", 100)
                );

                //No commit shouldn't insert
            }


            try (DBTX tx = db.begin()) {
                Scanner scanner = new Scanner(
                        Comparison.GE,
                        new DBRecord().addInt64("age", 25),

                        Comparison.LE,
                        new DBRecord().addInt64("age", Long.MAX_VALUE)
                );

                tx.scan("users", scanner);

                DBRecord row = new DBRecord();

                while (scanner.valid()) {
                    scanner.deref(row);

                    System.out.println(
                            "id=" + row.get("id").i64
                                    +", name=" + new String(row.get("name").str)
                                    + ", age=" + row.get("age").i64
                    );

                    scanner.next();
                }

                tx.commit();
            }
        }
    }
}
