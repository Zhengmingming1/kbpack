package com.kbpack.parser;

public interface Parser {
    boolean canHandle(PackageContext context);
    ParseResult parse(PackageContext context);
    int priority();
}
