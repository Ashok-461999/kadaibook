package com.kadaibook.storage;

public interface StorageService {

    void put(String key, byte[] data, String contentType);

    byte[] get(String key);

    boolean exists(String key);

    void delete(String key);
}
