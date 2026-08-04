package com.ntropy.common.dto.defense.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseModeEnterCommand {
    private Long userId;
    private String causeCode;
    private LocalDate unavailableStartDate;
    private LocalDate expectedReturnDate;
}
