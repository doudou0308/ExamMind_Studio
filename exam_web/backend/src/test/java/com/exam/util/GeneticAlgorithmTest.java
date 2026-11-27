package com.exam.util;

import com.exam.model.Question;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GeneticAlgorithmTest {
    private List<Question> pool(int size) {
        return IntStream.rangeClosed(1, size).mapToObj(id -> {
            Question question = new Question();
            question.setId(id);
            question.setCategory("基础");
            question.setDifficulty(3);
            return question;
        }).collect(Collectors.toList());
    }

    @Test
    void zeroQuestionsReturnsEmptyPaper() {
        assertTrue(GeneticAlgorithm.evolve(pool(3), 0, 3, Set.of()).isEmpty());
    }

    @Test
    void negativeQuestionCountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneticAlgorithm.evolve(pool(3), -1, 3, Set.of()));
    }

    @Test
    void undersizedPoolReturnsAvailableQuestions() {
        assertEquals(List.of(1, 2),
                GeneticAlgorithm.evolve(pool(2), 5, 3, Set.of()));
        assertTrue(GeneticAlgorithm.evolve(List.of(), 5, 3, Set.of()).isEmpty());
    }

    @Test
    void fullPoolPaperContainsEachQuestionExactlyOnce() {
        for (int run = 0; run < 10; run++) {
            List<Integer> paper = GeneticAlgorithm.evolve(pool(4), 4, 3, Set.of("基础"));
            assertEquals(4, paper.size());
            assertEquals(Set.of(1, 2, 3, 4), new HashSet<>(paper));
        }
    }

    @Test
    void partialPaperContainsUniqueIdsFromPool() {
        for (int run = 0; run < 10; run++) {
            List<Integer> paper = GeneticAlgorithm.evolve(pool(6), 3, 3, Set.of("基础"));
            assertEquals(3, paper.size());
            assertEquals(3, new HashSet<>(paper).size());
            assertTrue(Set.of(1, 2, 3, 4, 5, 6).containsAll(paper));
        }
    }
}
