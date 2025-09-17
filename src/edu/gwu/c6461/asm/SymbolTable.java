package edu.gwu.c6461.asm;

import java.util.*;

class SymbolTable {
    private final Map<String, Integer> map = new HashMap<>();
    boolean contains(String key) { return map.containsKey(key); }
    int get(String key) { return map.get(key); }
    void put(String key, int value) { map.put(key, value); }
    boolean containsKey(String key) { return map.containsKey(key); }
}
