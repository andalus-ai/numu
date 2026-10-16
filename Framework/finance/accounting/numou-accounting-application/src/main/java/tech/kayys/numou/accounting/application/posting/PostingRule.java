package tech.kayys.syirkah.accounting.application.posting;

public interface PostingRule<E> {
    boolean supports(Object event);
    PostingInstruction create(E event);
}
