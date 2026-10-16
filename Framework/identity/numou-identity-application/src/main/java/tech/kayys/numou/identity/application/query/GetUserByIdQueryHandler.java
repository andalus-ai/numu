package tech.kayys.syirkah.identity.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.identity.application.port.UserRepository;
import tech.kayys.syirkah.identity.domain.user.User;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Objects;

public final class GetUserByIdQueryHandler
        implements QueryHandler<GetUserByIdQuery, UserView> {

    private final UserRepository userRepository;

    public GetUserByIdQueryHandler(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    @Override
    public Uni<UserView> handle(GetUserByIdQuery query) {
        return userRepository.findById(UserId.of(query.userId()))
                .map(maybeUser -> maybeUser
                        .map(GetUserByIdQueryHandler::toView)
                        .orElseThrow(() -> ApplicationError.of(
                                "USER_NOT_FOUND",
                                "User does not exist"
                        ).toException())
                );
    }

    static UserView toView(User user) {
        return new UserView(
                user.id().value(),
                user.email().value(),
                user.displayName().value(),
                user.status()
        );
    }

}
