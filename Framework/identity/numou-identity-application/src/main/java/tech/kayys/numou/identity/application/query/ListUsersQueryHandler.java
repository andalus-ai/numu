package tech.kayys.syirkah.identity.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.page.Page;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.identity.application.port.UserRepository;

import java.util.Objects;
import java.util.stream.Collectors;

public final class ListUsersQueryHandler
        implements QueryHandler<ListUsersQuery, Page<UserView>> {

    private final UserRepository userRepository;

    public ListUsersQueryHandler(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    @Override
    public Uni<Page<UserView>> handle(ListUsersQuery query) {
        return userRepository.findAll(query.page())
                .map(page -> new Page<>(
                        page.content().stream()
                                .map(GetUserByIdQueryHandler::toView)
                                .collect(Collectors.toList()),
                        page.totalElements(),
                        page.page(),
                        page.size()
                ));
    }

}
