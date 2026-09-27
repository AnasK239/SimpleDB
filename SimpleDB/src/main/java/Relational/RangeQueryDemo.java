package Relational;

import BTree.BIter;
import KV.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class RangeQueryDemo {
    public static void main(String[] args) throws IOException {

        try (KV db = new KV(Path.of("iterator-demo.db"))) {
            db.open();

            db.set(
                    "apple".getBytes(StandardCharsets.UTF_8),
                    "red".getBytes(StandardCharsets.UTF_8)
            );

            db.set(
                    "banana".getBytes(StandardCharsets.UTF_8),
                    "yellow".getBytes(StandardCharsets.UTF_8)
            );

            db.set(
                    "cherry".getBytes(StandardCharsets.UTF_8),
                    "dark red".getBytes(StandardCharsets.UTF_8)
            );

            BIter iterator = db.seek(
                    "banana".getBytes(StandardCharsets.UTF_8),
                    Comparison.GE
            );

            while (iterator.valid()) {
                KVPair pair = iterator.deref();

                System.out.println(
                        new String(pair.key(), StandardCharsets.UTF_8)
                                + " -> "
                                + new String(pair.value(), StandardCharsets.UTF_8)
                );

                iterator.next();
            }
        } catch (Exception e){

        }
    }
}
