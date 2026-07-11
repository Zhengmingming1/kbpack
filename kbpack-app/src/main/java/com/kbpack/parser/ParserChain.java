package com.kbpack.parser;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ParserChain {
    private final List<Parser> parsers;

    public ParserChain(List<Parser> parsers) {
        this.parsers = parsers.stream().sorted(Comparator.comparingInt(Parser::priority)).toList();
    }

    public ParseResult parse(PackageContext context) {
        return parsers.stream()
                .filter(parser -> parser.canHandle(context))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PARSE_FAILED, "没有解析器可处理该知识包"))
                .parse(context);
    }

    List<Parser> parsers() { return parsers; }
}
