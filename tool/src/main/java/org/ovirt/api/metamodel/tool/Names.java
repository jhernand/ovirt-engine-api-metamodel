/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.tool;

import static java.util.stream.Collectors.joining;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.ovirt.api.metamodel.concepts.Name;

/**
 * This class contains methods useful to do computations with names.
 */
@ApplicationScoped
public class Names {
    @Inject Words words;

    public Name getPlural(Name singular) {
        List<String> all = singular.getWords();
        String last = all.get(all.size() - 1);
        last = words.getPlural(last);
        all.set(all.size() - 1, last);
        return new Name(all);
    }

    public Name getSingular(Name plural) {
        List<String> all = plural.getWords();
        String last = all.get(all.size() - 1);
        last = words.getSingular(last);
        all.set(all.size() - 1, last);
        return new Name(all);
    }

    /**
     * Capitalizes the words of the name and joins them together.
     */
    public String getCapitalized(Name name) {
        return name.words().map(words::capitalize).collect(joining());
    }

    /**
     * Converts the words of the given name to upper case and joins them using the given separator.
     */
    public String getUpperJoined(Name name, String separator) {
        return name.words().map(String::toUpperCase).collect(joining(separator));
    }

    /**
     * Converts the words of the given name to lower case and joins them using the given separator.
     */
    public String getLowerJoined(Name name, String separator) {
        return name.words().map(String::toLowerCase).collect(joining(separator));
    }

    /**
     * Returns a new Name, containing the words of both provided names,
     * in the order in which they were provided.
     */
    public Name concatenate(Name name1, Name name2) {
        Name name = new Name(name1.getWords());
        name.addWords(name2.getWords());
        return name;
    }
}

