package com.calipso.statistics;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StatisticsPeriod(LocalDateTime startInclusive, LocalDateTime endExclusive) {
    public static StatisticsPeriod of(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit précéder ou être égale à la date de fin.");
        }
        if (LocalDate.MAX.equals(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de fin hors limites.");
        }
        return new StatisticsPeriod(startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay());
    }
}
