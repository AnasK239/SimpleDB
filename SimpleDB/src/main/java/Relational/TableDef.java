package Relational;

public class TableDef {

    public TableDef() {

    }

    public String name;

    public int[] types;

    public String []cols;

    public int PKeys;

    public long prefix;


    public TableDef(String name, int[] types, String[] cols, int PKeys, long prefix) {
        this.name = name;
        this.types = types;
        this.cols = cols;
        this.PKeys = PKeys;
        this.prefix = prefix;
    }


}
