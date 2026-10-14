package com.example.serialposition.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Source of the word lists used in the experiment.
 *
 * Words are grouped into themes. Everything else follows from that grouping:
 * <ul>
 *   <li>Normal list: ten words, each from a different theme, so no two are related.</li>
 *   <li>Semantic list: positions 4-7 are four words from one theme; positions 1-3 and
 *       8-10 are six words from six other, mutually different themes.</li>
 * </ul>
 * Both lists of a session are drawn from disjoint themes, so no word or theme repeats
 * between the two tests. Theme membership is hand-curated; it keeps related words
 * out of the unrelated positions but is not a formal measure of semantic distance.
 */
@Component
public class WordBank {

    public static final int LIST_SIZE = 10;
    public static final int CLUSTER_START = 3;  // zero-based index of position 4
    public static final int CLUSTER_SIZE = 4;   // positions 4-7

    private static final int FILLERS_BEFORE_CLUSTER = 3;
    private static final int FILLER_COUNT = LIST_SIZE - CLUSTER_SIZE;
    private static final int THEMES_REQUIRED = LIST_SIZE + 1 + FILLER_COUNT;

    public record ListPair(List<String> normal, List<String> semantic) {
    }

    private final Map<String, List<String>> themes = new LinkedHashMap<>();
    private final Map<String, String> themeByWord = new HashMap<>();

    public WordBank() {
        define("Furniture", "Table", "Wood", "Furniture", "Chair", "Sofa", "Shelf");
        define("Medicine", "Doctor", "Hospital", "Nurse", "Medicine", "Patient", "Surgery");
        define("Weather", "Rain", "Storm", "Fog", "Thunder", "Umbrella", "Lightning");
        define("Seaside", "Ocean", "Wave", "Beach", "Sand", "Island", "Coast");
        define("Kitchen", "Fridge", "Spoon", "Plate", "Kettle", "Pan", "Fork");
        define("Music", "Guitar", "Piano", "Melody", "Drum", "Song", "Singer");
        define("School", "Teacher", "Pencil", "Classroom", "Textbook", "Homework", "Exam");
        define("Farm", "Barn", "Tractor", "Farmer", "Hay", "Harvest", "Silo");
        define("Space", "Moon", "Planet", "Rocket", "Galaxy", "Orbit", "Astronaut");
        define("Clothing", "Shirt", "Jacket", "Sock", "Scarf", "Glove", "Boots");
        define("Garden", "Flower", "Garden", "Petal", "Rose", "Tulip", "Lawn");
        define("Railway", "Train", "Station", "Ticket", "Track", "Platform", "Railway");
        define("Fire", "Fire", "Flame", "Smoke", "Ash", "Ember", "Torch");
        define("Money", "Bank", "Coin", "Wallet", "Cash", "Salary", "Loan");
        define("Football", "Ball", "Goal", "Referee", "Stadium", "Team", "Whistle");
        define("Jungle", "Tiger", "Lion", "Jungle", "Monkey", "Elephant", "Zebra");
        define("Computer", "Computer", "Keyboard", "Screen", "Internet", "Laptop", "Printer");

        if (themes.size() < THEMES_REQUIRED) {
            throw new IllegalStateException("At least " + THEMES_REQUIRED + " themes are required, found " + themes.size());
        }
    }

    private void define(String theme, String... words) {
        if (words.length < CLUSTER_SIZE) {
            throw new IllegalStateException("Theme '" + theme + "' needs at least " + CLUSTER_SIZE + " words");
        }
        themes.put(theme, List.of(words));
        for (String word : words) {
            if (themeByWord.put(normalize(word), theme) != null) {
                throw new IllegalStateException("Word '" + word + "' appears in more than one theme");
            }
        }
    }

    public static String normalize(String word) {
        return word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
    }

    public String themeOf(String word) {
        return themeByWord.get(normalize(word));
    }

    public ListPair generate(Random random) {
        List<String> themeNames = new ArrayList<>(themes.keySet());
        Collections.shuffle(themeNames, random);

        List<String> normal = new ArrayList<>();
        for (String theme : themeNames.subList(0, LIST_SIZE)) {
            normal.add(randomWord(theme, random));
        }
        Collections.shuffle(normal, random);

        List<String> clusterPool = new ArrayList<>(themes.get(themeNames.get(LIST_SIZE)));
        Collections.shuffle(clusterPool, random);
        List<String> cluster = clusterPool.subList(0, CLUSTER_SIZE);

        List<String> fillers = new ArrayList<>();
        for (String theme : themeNames.subList(LIST_SIZE + 1, THEMES_REQUIRED)) {
            fillers.add(randomWord(theme, random));
        }

        List<String> semantic = new ArrayList<>(fillers.subList(0, FILLERS_BEFORE_CLUSTER));
        semantic.addAll(cluster);
        semantic.addAll(fillers.subList(FILLERS_BEFORE_CLUSTER, FILLER_COUNT));

        return new ListPair(List.copyOf(normal), List.copyOf(semantic));
    }

    private String randomWord(String theme, Random random) {
        List<String> words = themes.get(theme);
        return words.get(random.nextInt(words.size()));
    }

    /** True if the list has ten distinct bank words, each from a different theme. */
    public boolean isValidNormalList(List<String> words) {
        if (words.size() != LIST_SIZE) {
            return false;
        }
        Set<String> usedThemes = new HashSet<>();
        for (String word : words) {
            String theme = themeOf(word);
            if (theme == null || !usedThemes.add(theme)) {
                return false;
            }
        }
        return true;
    }

    /** True if positions 4-7 share one theme and the other six words come from six other distinct themes. */
    public boolean isValidSemanticList(List<String> words) {
        if (words.size() != LIST_SIZE) {
            return false;
        }
        Set<String> distinctWords = new HashSet<>();
        for (String word : words) {
            if (!distinctWords.add(normalize(word))) {
                return false;
            }
        }
        String clusterTheme = themeOf(words.get(CLUSTER_START));
        if (clusterTheme == null) {
            return false;
        }
        Set<String> fillerThemes = new HashSet<>();
        for (int i = 0; i < words.size(); i++) {
            String theme = themeOf(words.get(i));
            boolean inCluster = i >= CLUSTER_START && i < CLUSTER_START + CLUSTER_SIZE;
            if (theme == null) {
                return false;
            }
            if (inCluster) {
                if (!theme.equals(clusterTheme)) {
                    return false;
                }
            } else if (theme.equals(clusterTheme) || !fillerThemes.add(theme)) {
                return false;
            }
        }
        return true;
    }
}
