package com.ntropy.bff.dto.defense.request;

import com.ntropy.common.dto.defense.command.DefenseModeEnterCommand;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class DefenseModeEnterRequest {
    private Long userId;
    private String causeCode;
    private LocalDate unavailableStartDate;
    private LocalDate expectedReturnDate;

    public DefenseModeEnterCommand toCommand() {
        return new DefenseModeEnterCommand(userId, causeCode, unavailableStartDate, expectedReturnDate);
    }
}
