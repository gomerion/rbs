package ru.ssau.rbs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.booking")
public class BookingProperties {

    private int gridMinutes = 15;
    private int minDurationMinutes = 15;
    private int maxDurationMinutes = 180;
    private int advanceDays = 30;
    private int maxActiveEmployee = 5;
    private int maxActiveManager = 10;
    private int cancelHoursEmployee = 2;
    private int seriesMax = 8;
}
