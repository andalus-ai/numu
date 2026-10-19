package tech.kayys.numou.leave.application.command;

import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.leave.domain.LeaveRequest;
import tech.kayys.numou.leave.domain.LeaveRequestId;
import tech.kayys.numou.leave.spi.LeaveRequestRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class SubmitLeaveRequestHandler {

    private final LeaveRequestRepository leaveRequestRepository;

    public SubmitLeaveRequestHandler(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = Objects.requireNonNull(leaveRequestRepository);
    }

    public CompletionStage<Result<LeaveRequestId>> handle(SubmitLeaveRequestCommand cmd) {
        LeaveRequestId id = LeaveRequestId.generate();
        LeaveRequest request = LeaveRequest.create(
                id, cmd.subjectId(), cmd.contextId(), cmd.leaveTypeId(),
                cmd.startDate(), cmd.endDate(), cmd.amount(), cmd.reason());
        request.submit();
        return leaveRequestRepository.save(request)
                .thenApply(saved -> Result.success(id));
    }
}
