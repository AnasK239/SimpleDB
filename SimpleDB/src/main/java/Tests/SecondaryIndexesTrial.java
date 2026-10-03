package Tests;

import KV.Comparison;
import Relational.DB;
import Relational.DBRecord;
import Relational.Scanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class SecondaryIndexesTrial {
    public static void main(String[] args) throws Exception {

        try (DB db = new DB(Path.of("tables-ch10.db"))) {
            db.open();

            // Run creation once for this fresh database.
//            db.tableNew(new TableDef(
//                    "users",
//
//                    new int[]{
//                            Value.TYPE_INT64,
//                            Value.TYPE_BYTES,
//                            Value.TYPE_INT64
//                    },
//
//                    new String[]{"id", "name", "age"},
//
//                    new String[][]{
//                            {"id"},   // Primary index.
//                            {"age"}   // Becomes (age, id).
//                    }
//            ));

            db.insert(
                    "users",
                    new DBRecord()
                            .addInt64("id", 7)
                            .addStr(
                                    "name",
                                    "Anas".getBytes(StandardCharsets.UTF_8)
                            )
                            .addInt64("age", 25)
            );

            db.insert(
                    "users",
                    new DBRecord()
                            .addInt64("id", 12)
                            .addStr(
                                    "name",
                                    "Raya".getBytes(StandardCharsets.UTF_8)
                            )
                            .addInt64("age", 25)
            );

            db.insert(
                    "users",
                    new DBRecord()
                            .addInt64("id", 19)
                            .addStr(
                                    "name",
                                    "Mahmoud".getBytes(StandardCharsets.UTF_8)
                            )
                            .addInt64("age", 30)
            );

            // age >= 25 AND age <= 25: everyone aged exactly 25.
            Scanner scanner = new Scanner(
                    Comparison.GE,
                    new DBRecord().addInt64("age", 25),

                    Comparison.LE,
                    new DBRecord().addInt64("age", 25)
            );

            db.scan("users", scanner);

            DBRecord row = new DBRecord();

            while (scanner.valid()) {
                scanner.deref(row);

                System.out.println(
                        row.get("id").i64
                                + ": "
                                + new String(
                                row.get("name").str,
                                StandardCharsets.UTF_8
                        )
                );

                scanner.next();
            }

            // age >= 25
            Scanner scanner2 = new Scanner(
                    Comparison.GE,
                    new DBRecord().addInt64("age", 25),

                    Comparison.LE,
                    new DBRecord().addInt64("age", Long.MAX_VALUE)
            );

            db.scan("users", scanner2);

            DBRecord row2 = new DBRecord();

            while (scanner2.valid()) {
                scanner2.deref(row2);

                System.out.println(
                        "name= " + new String(row2.get("name").str , StandardCharsets.UTF_8) +
                        ", id= " + row2.get("id").i64
                                + ", age=" + row2.get("age").i64
                );

                scanner2.next();
            }
        }
    }
}
