package com.ntropy.bff.dto.defense.request;

import com.ntropy.common.dto.defense.command.DefenseModeReleaseCommand;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class DefenseModeReleaseRequest {
    private Long userId;
    private LocalDate returnDate;

    public DefenseModeReleaseCommand toCommand() {
        return new DefenseModeReleaseCommand(userId, returnDate);
    }
}
