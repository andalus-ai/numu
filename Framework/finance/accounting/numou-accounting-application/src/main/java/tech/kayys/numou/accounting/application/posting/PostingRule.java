package tech.kayys.numou.accounting.application.posting;

public interface PostingRule<E> {
    boolean supports(Object event);
    PostingInstruction create(E event);
}
