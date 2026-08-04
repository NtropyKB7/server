package com.ntropy.common.client;

import com.ntropy.common.dto.defense.command.DefenseModeEnterCommand;
import com.ntropy.common.dto.defense.command.DefenseModeReleaseCommand;
import com.ntropy.common.dto.defense.summary.DefenseModeSummary;

public interface DefenseModeCommandClient {
    DefenseModeSummary enter(DefenseModeEnterCommand command);
    DefenseModeSummary release(Long defenseId, DefenseModeReleaseCommand command);
}
