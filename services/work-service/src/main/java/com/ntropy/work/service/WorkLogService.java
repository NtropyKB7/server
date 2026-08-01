package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkLogService {

    private static final String STATUS_PLANNED = "PLANNED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private final WorkLogMapper workLogMapper;
    private final JobService jobService;

    /**
     * 근무 계획 등록. fatigue 미입력 시 job.baseFatigue를 기본값으로 채운다.
     * estimated_income은 taskPerHour 기반 예측값이다(taskCount는 아직 없음).
     */
    @Transactional
    public WorkLog registerPlan(WorkLog workLog) {
        validatePlan(workLog);
        validateNoOverlap(workLog.getUserId(), workLog.getWorkDate(), workLog.getStartTime(), workLog.getEndTime(), null);
        Job job = jobService.findById(workLog.getJobId());

        if (workLog.getFatigue() == null) {
            workLog.setFatigue(job.getBaseFatigue().longValue());
        }
        workLog.setTaskCount(null);
        workLog.setEstimatedIncome(
                calculateEstimatedIncome(job, workLog.getStartTime(), workLog.getEndTime(), null));
        workLog.setStatus(STATUS_PLANNED);
        workLog.setSettlementStatus(SettlementStatus.NONE);

        workLogMapper.insert(workLog);
        return workLog;
    }

    /**
     * 계획 외 근무일지 등록. 실제 데이터를 받아 즉시 CONFIRMED로 생성한다.
     */
    @Transactional
    public WorkLog registerActual(WorkLog workLog) {
        validateActual(workLog);
        validateNoOverlap(workLog.getUserId(), workLog.getWorkDate(), workLog.getStartTime(), workLog.getEndTime(), null);
        Job job = jobService.findById(workLog.getJobId());
        validateTaskCountIfPerTask(job, workLog.getTaskCount());

        workLog.setEstimatedIncome(
                calculateEstimatedIncome(job, workLog.getStartTime(), workLog.getEndTime(), workLog.getTaskCount()));
        workLog.setStatus(STATUS_CONFIRMED);
        workLog.setSettlementStatus(SettlementStatus.PENDING);

        workLogMapper.insert(workLog);
        return workLog;
    }

    /**
     * 근무일지 수정. PLANNED/CONFIRMED 둘 다 가능하며 상태는 그대로 유지한다.
     * 넘어온 필드만 덮어쓰고 나머지는 기존 값을 유지한다.
     */
    @Transactional
    public WorkLog editWorkLog(Long logId, WorkLog patch) {
        WorkLog existing = findById(logId);
        applyPatch(existing, patch);
        validateNoOverlap(existing.getUserId(), existing.getWorkDate(), existing.getStartTime(), existing.getEndTime(),
                existing.getLogId());

        Job job = jobService.findById(existing.getJobId());
        existing.setEstimatedIncome(
                calculateEstimatedIncome(job, existing.getStartTime(), existing.getEndTime(), existing.getTaskCount()));

        workLogMapper.update(existing);
        return existing;
    }

    /**
     * 근무일지 확정. PLANNED 상태에서만 가능하다. jobId/시간/건수/피로도를 전부
     * 받아 덮어쓸 수 있고(사진 화면 기준), 최종적으로 CONFIRMED로 전환한다.
     */
    @Transactional
    public WorkLog confirmWorkLog(Long logId, WorkLog patch) {
        WorkLog existing = findById(logId);
        if (STATUS_CONFIRMED.equals(existing.getStatus())) {
            throw new IllegalStateException("이미 확정된 근무일지입니다. logId=" + logId);
        }
        applyPatch(existing, patch);
        validateNoOverlap(existing.getUserId(), existing.getWorkDate(), existing.getStartTime(), existing.getEndTime(),
                existing.getLogId());

        Job job = jobService.findById(existing.getJobId());
        validateTaskCountIfPerTask(job, existing.getTaskCount());

        existing.setEstimatedIncome(
                calculateEstimatedIncome(job, existing.getStartTime(), existing.getEndTime(), existing.getTaskCount()));
        existing.setStatus(STATUS_CONFIRMED);
        existing.setSettlementStatus(SettlementStatus.PENDING);

        workLogMapper.update(existing);
        return existing;
    }

    @Transactional
    public void deleteWorkLog(Long logId) {
        findById(logId);
        workLogMapper.deleteById(logId);
    }

    public WorkLog findById(Long logId) {
        WorkLog workLog = workLogMapper.findById(logId);
        if (workLog == null) {
            throw new IllegalArgumentException("존재하지 않는 근무일지입니다. logId=" + logId);
        }
        return workLog;
    }

    private void applyPatch(WorkLog existing, WorkLog patch) {
        if (patch.getJobId() != null) {
            existing.setJobId(patch.getJobId());
        }
        if (patch.getStartTime() != null) {
            existing.setStartTime(patch.getStartTime());
        }
        if (patch.getEndTime() != null) {
            existing.setEndTime(patch.getEndTime());
        }
        if (patch.getTaskCount() != null) {
            existing.setTaskCount(patch.getTaskCount());
        }
        if (patch.getFatigue() != null) {
            existing.setFatigue(patch.getFatigue());
        }
    }

    /**
     * 자정을 넘기는 근무(예: 23:30~02:00)는 24시간을 더해 정상 계산한다.
     */
    private Long calculateEstimatedIncome(Job job, LocalTime startTime, LocalTime endTime, Long taskCount) {
        if (startTime == null || endTime == null) {
            return null;
        }
        if (startTime.equals(endTime)) {
            throw new IllegalArgumentException("시작 시간과 종료 시간이 같을 수 없습니다.");
        }
        double hours = WorkTimeUtils.durationMinutes(startTime, endTime) / 60.0;

        switch (job.getSettlementType()) {
            case HOURLY:
                return job.getHourlyWage() == null ? null : Math.round(job.getHourlyWage() * hours);
            case PER_TASK:
                if (taskCount != null) {
                    return job.getPerTaskWage() == null ? null : job.getPerTaskWage() * taskCount;
                }
                if (job.getPerTaskWage() == null || job.getTaskPerHour() == null) {
                    return null;
                }
                return Math.round(job.getPerTaskWage() * job.getTaskPerHour() * hours);
            case MONTHLY:
                return null;
            default:
                throw new IllegalStateException("알 수 없는 정산 방식입니다: " + job.getSettlementType());
        }
    }

    /**
     * 같은 userId + workDate 내에서 시간대가 겹치는 다른 근무일지가 있는지 검사한다(잡 무관).
     * excludeLogId는 수정/확정 시 자기 자신의 기존 레코드를 비교 대상에서 제외하기 위함이다.
     */
    private void validateNoOverlap(Long userId, LocalDate workDate, LocalTime startTime, LocalTime endTime,
                                    Long excludeLogId) {
        for (WorkLog other : workLogMapper.findByUserIdAndWorkDate(userId, workDate)) {
            if (excludeLogId != null && excludeLogId.equals(other.getLogId())) {
                continue;
            }
            if (WorkTimeUtils.isOverlapping(startTime, endTime, other.getStartTime(), other.getEndTime())) {
                throw new IllegalArgumentException(
                        "해당 시간대에 이미 등록된 근무일지가 있습니다. workDate=" + workDate);
            }
        }
    }

    private void validateTaskCountIfPerTask(Job job, Long taskCount) {
        if (SettlementType.PER_TASK.equals(job.getSettlementType()) && taskCount == null) {
            throw new IllegalArgumentException("건별 정산 잡은 확정 시 task_count가 필요합니다.");
        }
    }

    private void validatePlan(WorkLog workLog) {
        if (workLog.getUserId() == null) {
            throw new IllegalArgumentException("user_id는 필수입니다.");
        }
        if (workLog.getJobId() == null) {
            throw new IllegalArgumentException("job_id는 필수입니다.");
        }
        if (workLog.getWorkDate() == null) {
            throw new IllegalArgumentException("work_date는 필수입니다.");
        }
        if (workLog.getStartTime() == null || workLog.getEndTime() == null) {
            throw new IllegalArgumentException("start_time/end_time은 필수입니다.");
        }
    }

    private void validateActual(WorkLog workLog) {
        validatePlan(workLog);
        if (workLog.getFatigue() == null) {
            throw new IllegalArgumentException("계획 외 등록은 fatigue가 필수입니다.");
        }
    }
}
