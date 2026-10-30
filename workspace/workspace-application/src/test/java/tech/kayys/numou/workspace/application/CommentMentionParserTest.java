package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommentMentionParserTest {
    @Test
    void extractsUniqueHandlesInFirstOccurrenceOrderCaseInsensitively() {
        assertEquals(List.of("Amina", "team.lead", "user_2"),
                CommentMentionParser.parse("Hi @Amina, @team.lead and @amina; also @user_2."));
    }

    @Test
    void doesNotTreatEmailAddressesOrEmbeddedIdentifiersAsMentions() {
        assertEquals(List.of("reviewer"),
                CommentMentionParser.parse("mail a@company.test, token_x@home and mention @reviewer."));
    }

    @Test
    void boundsUniqueMentionsAndIgnoresBlankBodies() {
        assertTrue(CommentMentionParser.parse("  ").isEmpty());
        String body = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "@member" + i).collect(java.util.stream.Collectors.joining(" "));
        assertThrows(IllegalArgumentException.class, () -> CommentMentionParser.parse(body));
    }
}
