package com.tradingagents.trading.dto;

import java.util.Optional;
import java.util.regex.Pattern;

public record PaperSignal(Action action, int percent) {

    private static final Pattern HEADER = Pattern.compile("\\AACTION: (BUY|SELL|HOLD)\\h*\\R(?:\\h*\\R)?SIZE: (\\d{1,3})%?\\h*(?:\\R|\\z)");

    public enum Action { BUY, SELL, HOLD }

    public static Optional<PaperSignal> from(String report) {
        if (report == null) return Optional.empty();
        var match = HEADER.matcher(report.strip());
        if (!match.find()) return Optional.empty();

        Action action = Action.valueOf(match.group(1));
        int percent = Integer.parseInt(match.group(2));
        if (percent > 100 || (action == Action.HOLD ? percent != 0 : percent == 0)) {
            return Optional.empty();
        }
        return Optional.of(new PaperSignal(action, percent));
    }
}
