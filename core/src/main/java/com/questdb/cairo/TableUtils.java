package com.questdb.cairo;

import com.questdb.factory.configuration.JournalMetadata;
import com.questdb.misc.Files;
import com.questdb.std.ThreadLocal;
import com.questdb.std.str.CompositePath;
import com.questdb.std.str.Path;

public class TableUtils {
    private final static ThreadLocal<CompositePath> tlPath = new ThreadLocal<>(CompositePath.FACTORY);
    private final static ThreadLocal<AppendMemory> tlMem = new ThreadLocal<>(AppendMemory::new);

    public static void create(CharSequence root, JournalMetadata metadata, int mode) {
        CompositePath path = tlPath.get();
        path.of(root).concat(metadata.getName()).put(Path.SEPARATOR).$();
        if (Files.mkdirs(path, mode) == -1) {
            throw new RuntimeException("cannot create dir");
        }

        int rootLen = path.length();
        path.trimTo(rootLen);

        try (AppendMemory mem = tlMem.get()) {

            mem.of(path.concat("_meta").$(), (int) Files.PAGE_SIZE, 0);

            int count = metadata.getColumnCount();
            mem.putInt(count);
            mem.putInt(metadata.getPartitionBy());
            for (int i = 0; i < count; i++) {
                mem.putInt(metadata.getColumnQuick(i).type);
            }
            for (int i = 0; i < count; i++) {
                mem.putStr(metadata.getColumnQuick(i).name);
            }


            path.trimTo(rootLen);
            mem.of(path.concat("_txi").$(), (int) Files.PAGE_SIZE, 0);


            // txn
            mem.putLong(0);
            // transient count
            mem.putLong(0);
            // fixed count
            mem.putLong(0);
            // last partition timestamp - identifier
            mem.putLong(Long.MIN_VALUE);

        }
    }

    public static int exists(CharSequence root, CharSequence name) {
        CompositePath path = tlPath.get();
        path.of(root).concat(name).$();
        if (Files.exists(path)) {
            // prepare to replace trailing \0
            path.trimTo(path.length());
            if (Files.exists(path.concat("_txi").$())) {
                return 0;
            } else {
                return 1;
            }
        } else {
            return 1;
        }
    }

    public static void freeThreadLocals() {
        tlMem.get().close();
        tlMem.remove();

        tlPath.get().close();
        tlPath.remove();
    }
}
