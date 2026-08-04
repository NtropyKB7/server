package com.ntropy.work.client;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.ExpectedIncomeLossQueryClient;
import com.ntropy.common.dto.work.summary.JobExpectedIncomeLossSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.service.JobService;

import lombok.RequiredArgsConstructor;

/**
 * 방어모드 기간 동안 근무하지 못해 발생할 예상 손실소득을 잡별로 계산한다.
 *
 * <p>잡 등록/수정 시점에 저장해둔 월 환산 예상 소득(monthly_expected_income)을
 * 방어기간 일수만큼 일할 계산한다(30일 기준). 월 예상 소득을 계산할 수 없는
 * 잡(PER_TASK 등)은 손실액을 null로 반환하며, 방어모드가 이를 계산 불가로 처리한다.</p>
 */
@Component
@RequiredArgsConstructor
public class LocalExpectedIncomeLossQueryClient implements ExpectedIncomeLossQueryClient {

    private static final int DAYS_PER_MONTH = 30;

    private final JobService jobService;

    @Override
    public List<JobExpectedIncomeLossSummary> findExpectedIncomeLossByJob(
            Long userId, LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        return jobService.findByUserId(userId).stream()
                .filter(job -> Boolean.TRUE.equals(job.getIsActive()))
                .map(job -> toSummary(job, days))
                .collect(Collectors.toList());
    }

    private JobExpectedIncomeLossSummary toSummary(Job job, long days) {
        return new JobExpectedIncomeLossSummary(
                job.getJobId(),
                job.getJobName(),
                calculateLoss(job.getMonthlyExpectedIncome(), days));
    }

    private Long calculateLoss(Long monthlyExpectedIncome, long days) {
        if (monthlyExpectedIncome == null) {
            return null;
        }
        return Math.round(monthlyExpectedIncome * ((double) days / DAYS_PER_MONTH));
    }
}
