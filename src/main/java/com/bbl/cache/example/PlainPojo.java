package com.bbl.cache.example;

/** Mutable getter/setter class representing a "plain object" cache value shape. */
@Deprecated
public class PlainPojo {

    private String id;
    private String name;

    public PlainPojo() {
    }

    public PlainPojo(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlainPojo other)) return false;
        return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name);
    }
}
