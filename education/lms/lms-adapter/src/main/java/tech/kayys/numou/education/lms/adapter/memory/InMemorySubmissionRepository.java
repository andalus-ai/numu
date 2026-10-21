package tech.kayys.numou.education.lms.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.education.lms.domain.Submission;
import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.education.lms.spi.SubmissionRepository;

public class InMemorySubmissionRepository implements SubmissionRepository {
    private final Map<SubmissionId, Submission> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Submission> save(Submission e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Submission>> findById(SubmissionId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(SubmissionId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Submission e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(SubmissionId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<Submission>> findByAssignmentAndStudent(AssignmentId assignmentId, StudentId studentId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(s -> s.getAssignmentId().equals(assignmentId) && s.getStudentId().equals(studentId)).findFirst());
    }
}
