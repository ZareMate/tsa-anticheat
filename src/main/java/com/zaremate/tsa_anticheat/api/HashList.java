package com.zaremate.tsa_anticheat.api;

/** Independent lists; adding an entry never removes it from another list. */
public enum HashList {
    ALLOWED("allowed_hashes.txt"),
    BLACKLISTED("blacklisted_hashes.txt"),
    ENFORCEMENT("enforcement_hashes.txt");

    final String filename;
    HashList(String filename) { this.filename = filename; }
}
