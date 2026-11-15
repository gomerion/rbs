package ru.ssau.rbs.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BookingResult {

    private BookingResponse response;
    private boolean replayed;
}
