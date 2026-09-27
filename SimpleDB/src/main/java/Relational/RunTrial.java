package Relational;
import BTree.UpdateResult;
import Relational.DB;
import Relational.DBRecord;
import Relational.TableDef;
import Relational.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
public class RunTrial {

    public static void main(String[] args) throws IOException {

        Path path = Path.of("tables.db");
        System.out.println("Database file: " + path.toAbsolutePath());

        try (DB db = new DB(path)) {
            db.open();

            db.tableNew(new TableDef(
                    "users",
                    new int[]{Value.TYPE_INT64, Value.TYPE_BYTES},
                    new String[]{"id", "name"},
                    1,
                    0
            ));

            UpdateResult inserted = db.insert(
                    "users",
                    new DBRecord()
                            .addInt64("id", 7)
                            .addStr(
                                    "name",
                                    "Anas".getBytes(StandardCharsets.UTF_8)
                            )
            );

            System.out.println(inserted.added());

            DBRecord lookup = new DBRecord().addInt64("id", 7);

            if (db.get("users", lookup)) {
                System.out.println(
                        new String(lookup.get("name").str, StandardCharsets.UTF_8)
                );
            }

            db.update(
                    "users",
                    new DBRecord()
                            .addInt64("id", 7)
                            .addStr(
                                    "name",
                                    "Updated name".getBytes(StandardCharsets.UTF_8)
                            )
            );


            DBRecord lookup2 = new DBRecord().addInt64("id", 7);

            if (db.get("users", lookup2)) {
                System.out.println(
                        new String(
                                lookup2.get("name").str,
                                StandardCharsets.UTF_8
                        )
                );
            }

            boolean deleted = db.delete(
                    "users",
                    new DBRecord().addInt64("id", 7)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    }

