package Relational;

// This class only holds predefined tables
public final class InternalTables {

    private InternalTables() {}

    //Holds table name -> schema of all tables
    public static final TableDef TDEF_TABLE = new TableDef(

            "@table",

            new int[]{Value.TYPE_BYTES, Value.TYPE_BYTES},

            new String[]{"name", "def"},
            1,
            2
    );

    //Holds any meta data (global prefix counter etc..)
    public static final TableDef TDEF_META = new TableDef(

            "@meta",

            new int[]{Value.TYPE_BYTES, Value.TYPE_BYTES},

            new String[]{"key", "val"},
            1,
            1
    );
}